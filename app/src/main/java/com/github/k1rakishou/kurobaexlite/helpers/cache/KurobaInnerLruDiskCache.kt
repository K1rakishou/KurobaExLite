package com.github.k1rakishou.kurobaexlite.helpers.cache

import android.content.Context
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.ConversionUtils
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.helpers.removeExtensionFromFileName
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import okhttp3.HttpUrl
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat

internal class KurobaInnerLruDiskCache(
  cacheDirFile: File,
  chunksCacheDirFile: File,
  private val appContext: Context,
  private val androidHelpers: AndroidHelpers,
  private val sharedDispatcher: CoroutineDispatcher,
  private val fileCacheDiskSizeBytes: Long,
  private val cacheFileType: CacheFileType,
  private val diskCacheCleanupRemovePercent: Int = 25,
  private val isDevBuild: Boolean
) {
  private val TAG = "KurobaInnerLruDiskCache{${cacheFileType.id}}"

  private val coroutineScope = CoroutineScope(sharedDispatcher + SupervisorJob())
  private val cacheHandlerSynchronizer = CacheHandlerSynchronizer<FileId>()

  /**
   * An estimation of the current size of the directory. Used to check if trim must be run
   * because the folder exceeds the maximum size.
   */
  private val size = AtomicLong()
  private val lastTrimTime = AtomicLong(0)
  private val trimRunning = AtomicBoolean(false)
  private val recalculationRunning = AtomicBoolean(false)
  private val trimChunksRunning = AtomicBoolean(false)

  @GuardedBy("itself")
  private val filesOnDiskCache = mutableSetWithCap<FileId>(128)
  @GuardedBy("itself")
  private val fullyDownloadedFiles = mutableSetWithCap<FileId>(128)

  private val _cacheDirFile: File = cacheDirFile
  private val cacheDirFile: File
    get() {
      if (!_cacheDirFile.exists()) {
        _cacheDirFile.mkdirs()

        synchronized(filesOnDiskCache) { filesOnDiskCache.clear() }
        synchronized(fullyDownloadedFiles) { fullyDownloadedFiles.clear() }
      }

      return _cacheDirFile
    }

  private val _chunksCacheDirFile: File = chunksCacheDirFile
  private val chunksCacheDirFile: File
    get() {
      if (!_chunksCacheDirFile.exists()) {
        _chunksCacheDirFile.mkdirs()
      }

      return _chunksCacheDirFile
    }

  init {
    logcat(TAG) {
      "cacheFileType=$cacheFileType, "
      "fileCacheDiskSize=${fileCacheDiskSizeBytes.asReadableFileSize()}"
    }

    backgroundRecalculateSize()
    backgroundClearChunksCacheDir()
  }

  fun getSize(): Long {
    return size.get()
  }

  fun getMaxSize(): Long {
    return fileCacheDiskSizeBytes
  }

  suspend fun cacheFileExistsInMemoryCache(url: HttpUrl): Boolean {
    val fileId = FileId.fromUrl(url)
    return synchronized(filesOnDiskCache) { filesOnDiskCache.contains(fileId) }
  }

  suspend fun getChunkCacheFileOrNull(chunkStart: Long, chunkEnd: Long, url: HttpUrl): File? {
    return withContext(sharedDispatcher) {
      val fileId = FileId.fromUrl(url)
      val chunkCacheFile = getChunkCacheFileInternal(chunkStart, chunkEnd, fileId)

      return@withContext cacheHandlerSynchronizer.withLocalLock(fileId) {
        try {
          if (chunkCacheFile.exists()) {
            return@withLocalLock chunkCacheFile
          }

          return@withLocalLock null
        } catch (error: Throwable) {
          logcat(TAG) { "Error while trying to get chunk cache file (deleting), ${error.asLog()}" }

          deleteCacheFile(fileId)
          return@withLocalLock null
        }
      }
    }
  }

  suspend fun getCacheFileOrNull(url: HttpUrl): File? {
    return withContext(sharedDispatcher) {
      val fileId = FileId.fromUrl(url)

      return@withContext cacheHandlerSynchronizer.withLocalLock(fileId) {
        try {
          val cacheFile = getCacheFileByFileId(fileId)
          if (!cacheFileExistsOnDisk(fileId)) {
            return@withLocalLock null
          }

          return@withLocalLock cacheFile
        } catch (error: IOException) {
          logcat(TAG) { "Error while trying to get cache file (deleting), ${error.asLog()}" }

          deleteCacheFile(fileId)
          return@withLocalLock null
        }
      }
    }
  }

  suspend fun getOrCreateCacheFile(url: HttpUrl): File? {
    return withContext(sharedDispatcher) {
      val fileId = FileId.fromUrl(url)

      return@withContext cacheHandlerSynchronizer.withLocalLock(fileId) {
        try {
          val cacheFile = getCacheFileByFileId(fileId)
          if (!cacheFile.exists() && !cacheFile.createNewFile()) {
            throw IOException("Couldn't create cache file, path = ${cacheFile.absolutePath}")
          }

          val cacheFileMeta = getCacheFileMetaByFileId(fileId)
          if (!cacheFileMeta.exists()) {
            if (!cacheFileMeta.createNewFile()) {
              throw IOException("Couldn't create cache file meta, path = ${cacheFileMeta.absolutePath}")
            }

            val result = updateCacheFileMeta(
              fileId = fileId,
              overwrite = true,
              createdOn = System.currentTimeMillis(),
              fileDownloaded = false
            )

            if (!result) {
              throw IOException("Cache file meta update failed")
            }
          }

          synchronized(filesOnDiskCache) { filesOnDiskCache.add(fileId) }
          return@withLocalLock cacheFile
        } catch (error: IOException) {
          logcat(TAG) { "Error while trying to get or create cache file (deleting), ${error.asLog()}" }

          deleteCacheFile(fileId)
          return@withLocalLock null
        }
      }
    }
  }

  suspend fun getOrCreateChunkCacheFile(chunkStart: Long, chunkEnd: Long, url: HttpUrl): File? {
    return withContext(sharedDispatcher) {
      val fileId = FileId.fromUrl(url)
      val chunkCacheFile = getChunkCacheFileInternal(chunkStart, chunkEnd, fileId)

      return@withContext cacheHandlerSynchronizer.withLocalLock(fileId) {
        try {
          if (chunkCacheFile.exists()) {
            if (!chunkCacheFile.delete()) {
              throw IOException("Couldn't delete old chunk cache file")
            }
          }

          if (!chunkCacheFile.createNewFile()) {
            throw IOException("Couldn't create new chunk cache file")
          }

          return@withLocalLock chunkCacheFile
        } catch (error: Throwable) {
          logcat(TAG) { "Error while trying to get or create chunk cache file (deleting), ${error.asLog()}" }

          deleteCacheFile(fileId)
          return@withLocalLock null
        }
      }
    }
  }

  suspend fun cacheFileExistsOnDisk(file: File): Boolean {
    return cacheFileExistsOnDisk(FileId.fromFile(file))
  }

  suspend fun cacheFileExistsOnDisk(fileId: FileId): Boolean {
    return withContext(sharedDispatcher) {
      return@withContext cacheHandlerSynchronizer.withLocalLock(fileId) {
        try {
          val containsInCache = synchronized(fullyDownloadedFiles) { fullyDownloadedFiles.contains(fileId) }
          if (containsInCache) {
            return@withLocalLock true
          }

          val cacheFile = getCacheFileByFileId(fileId)

          if (!cacheFile.exists()) {
            deleteCacheFile(fileId)
            return@withLocalLock false
          }

          if (!cacheFile.name.endsWith(CACHE_EXTENSION)) {
            logcat(TAG) { "Not a cache file (deleting). file: ${cacheFile.absolutePath}" }
            deleteCacheFile(fileId)
            return@withLocalLock false
          }

          val cacheFileMetaFile = getCacheFileMetaByFileId(fileId)
          if (!cacheFileMetaFile.exists()) {
            logcat(TAG) { "Cache file meta does not exist (deleting). cacheFileMetaFile: ${cacheFileMetaFile.absolutePath}" }
            deleteCacheFile(fileId)
            return@withLocalLock false
          }

          if (cacheFileMetaFile.length() <= 0) {
            logcat(TAG) { "Cache file meta is empty (deleting). cacheFileMetaFile: ${cacheFileMetaFile.absolutePath}" }
            deleteCacheFile(fileId)
            return@withLocalLock false
          }

          val cacheFileMeta = readCacheFileMeta(cacheFileMetaFile)
          if (cacheFileMeta == null) {
            logcat(TAG) { "Failed to read cache file meta (deleting). cacheFileMetaFile: ${cacheFileMetaFile.absolutePath}" }
            deleteCacheFile(fileId)
            return@withLocalLock false
          }

          val isDownloaded = cacheFileMeta.isDownloaded
          if (isDownloaded) {
            synchronized(fullyDownloadedFiles) { fullyDownloadedFiles += fileId }
          } else {
            synchronized(fullyDownloadedFiles) { fullyDownloadedFiles -= fileId }
          }

          return@withLocalLock isDownloaded
        } catch (error: Throwable) {
          logcat(TAG) { "Error while trying to check whether the file is already downloaded, ${error.asLog()}" }
          deleteCacheFile(fileId)
          return@withLocalLock false
        }
      }
    }
  }

  suspend fun markFileDownloaded(output: File): Boolean {
    return withContext(sharedDispatcher) {
      val fileId = FileId.fromFile(output)

      return@withContext cacheHandlerSynchronizer.withLocalLock(fileId) {
        try {
          if (!output.exists()) {
            logcat(TAG) { "File does not exist (deleting). file: ${output.absolutePath}" }
            deleteCacheFile(fileId)
            return@withLocalLock false
          }

          val updateResult = updateCacheFileMeta(
            fileId = fileId,
            overwrite = false,
            createdOn = null,
            fileDownloaded = true
          )

          if (!updateResult) {
            val cacheFileMeta = getCacheFileMetaByFileId(fileId)

            logcatError(TAG) {
              "Failed to update cache file meta (deleting). " +
                "cacheFileMeta: ${cacheFileMeta.absolutePath}, output: ${output.absolutePath}"
            }

            deleteCacheFile(fileId)
          } else {
            synchronized(fullyDownloadedFiles) { fullyDownloadedFiles += fileId }
          }

          return@withLocalLock updateResult
        } catch (error: Throwable) {
          logcat(TAG) { "Error while trying to mark file as downloaded (deleting), ${error.asLog()}" }
          deleteCacheFile(fileId)
          return@withLocalLock false
        }
      }
    }
  }

  fun fileWasAdded(fileLen: Long): Long {
    val totalSize = size.addAndGet(fileLen.coerceAtLeast(0))
    val trimTime = lastTrimTime.get()
    val now = System.currentTimeMillis()

    val minTrimInterval = if (androidHelpers.isDevFlavor()) {
      0
    } else {
      // If the user scrolls through high-res images very fast we may end up in a situation
      // where the cache limit is hit but all the files in it were created earlier than
      // MIN_CACHE_FILE_LIFE_TIME ago. So in such case trim() will be called on EVERY
      // new opened image and since trim() is a pretty slow operation it may overload the
      // disk IO. So to avoid it we run trim() only once per MIN_TRIM_INTERVAL.
      MIN_TRIM_INTERVAL
    }

    val canRunTrim = totalSize > fileCacheDiskSizeBytes
      && now - trimTime > minTrimInterval
      && trimRunning.compareAndSet(false, true)

    if (canRunTrim) {
      coroutineScope.launch {
        try {
          trim()
        } catch (error: Throwable) {
          logcat(TAG) { "trim() error, ${error.asLog()}" }
        } finally {
          lastTrimTime.set(now)
          trimRunning.set(false)
        }
      }
    }

    return totalSize
  }

  suspend fun deleteCacheFile(cacheFile: File): Boolean {
    return withContext(sharedDispatcher) {
      val fileId = FileId.fromFile(cacheFile)
      deleteCacheFile(fileId)
    }
  }

  suspend fun deleteCacheFile(url: HttpUrl): Boolean {
    return withContext(sharedDispatcher) {
      val fileId = FileId.fromUrl(url)
      deleteCacheFile(fileId)
    }
  }

  private suspend fun deleteCacheFile(fileId: FileId): Boolean {
    return withContext(sharedDispatcher) {
      return@withContext cacheHandlerSynchronizer.withLocalLock(fileId) {
        val cacheFile = getCacheFileByFileId(fileId)
        val cacheMetaFile = getCacheFileMetaByFileId(fileId)
        val cacheFileSize = cacheFile.length()

        val deleteCacheFileResult = !cacheFile.exists() || cacheFile.delete()
        if (!deleteCacheFileResult) {
          logcat(TAG) { "Failed to delete cache file, fileName = ${cacheFile.absolutePath}" }
        }

        val deleteCacheFileMetaResult = !cacheMetaFile.exists() || cacheMetaFile.delete()
        if (!deleteCacheFileMetaResult) {
          logcat(TAG) { "Failed to delete cache file meta = ${cacheMetaFile.absolutePath}" }
        }

        synchronized(filesOnDiskCache) { filesOnDiskCache.remove(fileId) }
        synchronized(fullyDownloadedFiles) { fullyDownloadedFiles.remove(fileId) }

        if (deleteCacheFileResult && deleteCacheFileMetaResult) {
          val fileSize = if (cacheFileSize < 0) {
            0
          } else {
            cacheFileSize
          }

          if (fileSize > 0) {
            size.getAndAdd(-fileSize)
            if (size.get() < 0L) {
              size.set(0L)
            }

            if (isDevBuild) {
              logcat(TAG) {
                "Deleted ${cacheFile.name} and it's meta ${cacheMetaFile.name}, " +
                  "fileSize = ${fileSize.asReadableFileSize()}, " +
                  "cache size = ${size.get().asReadableFileSize()}"
              }
            }
          }

          return@withLocalLock true
        }

        // Only one of the files could be deleted
        return@withLocalLock false
      }
    }
  }

  suspend fun clearCache() {
    withContext(sharedDispatcher) {
      logcat(TAG) { "Clearing cache ${cacheFileType}" }

      cacheHandlerSynchronizer.withGlobalLock {
        if (cacheDirFile.exists() && cacheDirFile.isDirectory) {
          for (file in cacheDirFile.listFiles() ?: emptyArray()) {
            if (!deleteCacheFile(file)) {
              logcat(TAG) { "Could not delete cache file while clearing cache ${file.absolutePath}" }
            }
          }
        }

        if (chunksCacheDirFile.exists() && chunksCacheDirFile.isDirectory) {
          for (file in chunksCacheDirFile.listFiles() ?: emptyArray()) {
            if (!file.delete()) {
              logcat(TAG) { "Could not delete cache chunk file while clearing cache ${file.absolutePath}" }
            }
          }
        }

        synchronized(filesOnDiskCache) { filesOnDiskCache.clear() }
        synchronized(fullyDownloadedFiles) { fullyDownloadedFiles.clear() }

        recalculateSize()
      }
    }
  }

  private suspend fun updateCacheFileMeta(
    fileId: FileId,
    overwrite: Boolean,
    createdOn: Long?,
    fileDownloaded: Boolean?
  ): Boolean {
    return cacheHandlerSynchronizer.withLocalLock(fileId) {
      val file = getCacheFileMetaByFileId(fileId)
      if (!file.exists()) {
        logcat(TAG) { "Cache file meta does not exist!" }
        return@withLocalLock false
      }

      if (!file.name.endsWith(CACHE_META_EXTENSION)) {
        logcat(TAG) { "Not a cache file meta! file = ${file.absolutePath}" }
        return@withLocalLock false
      }

      val prevCacheFileMeta = readCacheFileMeta(file).let { cacheFileMeta ->
        when {
          !overwrite && cacheFileMeta != null -> {
            require(!(createdOn == null && fileDownloaded == null)) {
              "Only one parameter may be null when updating!"
            }

            val updatedCreatedOn = createdOn ?: cacheFileMeta.createdOn
            val updatedFileDownloaded = fileDownloaded ?: cacheFileMeta.isDownloaded

            return@let CacheFileMeta(
              CURRENT_META_FILE_VERSION,
              updatedCreatedOn,
              updatedFileDownloaded
            )
          }
          else -> {
            if (createdOn == null || fileDownloaded == null) {
              throw IOException(
                "Both parameters must not be null when writing! " +
                  "(Probably prevCacheFileMeta couldn't be read, check the logs)"
              )
            }

            return@let CacheFileMeta(CURRENT_META_FILE_VERSION, createdOn, fileDownloaded)
          }
        }
      }

      return@withLocalLock file.outputStream().use { stream ->
        return@use PrintWriter(stream).use { pw ->
          val toWrite = String.format(
            Locale.ENGLISH,
            CACHE_FILE_META_CONTENT_FORMAT,
            CURRENT_META_FILE_VERSION,
            prevCacheFileMeta.createdOn,
            prevCacheFileMeta.isDownloaded
          )

          val lengthChars = ConversionUtils.intToCharArray(toWrite.length)
          pw.write(lengthChars)
          pw.write(toWrite)
          pw.flush()

          return@use true
        }
      }
    }
  }

  private fun readCacheFileMeta(cacheFileMeta: File): CacheFileMeta? {
    if (!cacheFileMeta.exists()) {
      throw IOException("Cache file meta does not exist, path = ${cacheFileMeta.absolutePath}")
    }

    if (!cacheFileMeta.isFile) {
      throw IOException("Input file is not a file!")
    }

    if (!cacheFileMeta.canRead()) {
      throw IOException("Couldn't read cache file meta")
    }

    if (cacheFileMeta.length() <= 0) {
      // This is a valid case
      return null
    }

    if (!cacheFileMeta.name.endsWith(CACHE_META_EXTENSION)) {
      throw IOException("Not a cache file meta! file = ${cacheFileMeta.absolutePath}")
    }

    return cacheFileMeta.reader().use { reader ->
      val lengthBuffer = CharArray(CACHE_FILE_META_HEADER_SIZE)

      var read = reader.read(lengthBuffer)
      if (read != CACHE_FILE_META_HEADER_SIZE) {
        throw IOException(
          "Couldn't read content size of cache file meta, read $read"
        )
      }

      val length = ConversionUtils.charArrayToInt(lengthBuffer)
      if (length < 0 || length > MAX_CACHE_META_SIZE) {
        throw IOException("Cache file meta is too big or negative (${length} bytes)." +
          " It was probably corrupted. Deleting it.")
      }

      val contentBuffer = CharArray(length)
      read = reader.read(contentBuffer)

      if (read != length) {
        throw IOException("Couldn't read content cache file meta, read = $read, expected = $length")
      }

      val content = String(contentBuffer)
      val split = content.split(",").toTypedArray()

      if (split.size != CacheFileMeta.PARTS_COUNT) {
        throw IOException("Couldn't split meta content ($content), split.size = ${split.size}")
      }

      val fileVersion = split[0].toInt()
      if (fileVersion != CURRENT_META_FILE_VERSION) {
        throw IOException("Bad file version: $fileVersion")
      }

      return@use CacheFileMeta(
        fileVersion,
        split[1].toLong(),
        split[2].toBoolean()
      )
    }
  }

  fun getCacheFileByUrl(url: HttpUrl): File {
    val fileId = FileId.fromUrl(url)
    val fileName = formatCacheFileName(fileId)
    return File(cacheDirFile, fileName)
  }

  fun getCacheFileByFileId(fileId: FileId): File {
    val fileName = formatCacheFileName(fileId)
    return File(cacheDirFile, fileName)
  }

  fun getCacheFileMetaByFileId(fileId: FileId): File {
    val fileName = formatCacheFileMetaName(fileId)
    return File(cacheDirFile, fileName)
  }

  private fun getChunkCacheFileInternal(
    chunkStart: Long,
    chunkEnd: Long,
    fileId: FileId
  ): File {
    val fileName = formatChunkCacheFileName(chunkStart, chunkEnd, fileId)
    return File(chunksCacheDirFile, fileName)
  }

  private fun formatChunkCacheFileName(
    chunkStart: Long,
    chunkEnd: Long,
    fileId: FileId
  ): String {
    return String.format(
      Locale.ENGLISH,
      CHUNK_CACHE_FILE_NAME_FORMAT,
      fileId.urlSha256,
      chunkStart,
      chunkEnd,
      CHUNK_CACHE_EXTENSION
    )
  }

  fun formatCacheFileName(fileId: FileId): String {
    return String.format(
      Locale.ENGLISH,
      CACHE_FILE_NAME_FORMAT,
      fileId.urlSha256,
      CACHE_EXTENSION
    )
  }

  private fun formatCacheFileMetaName(fileId: FileId): String {
    return String.format(
      Locale.ENGLISH,
      CACHE_FILE_NAME_FORMAT,
      fileId.urlSha256,
      CACHE_META_EXTENSION
    )
  }

  private fun backgroundClearChunksCacheDir() {
    if (trimChunksRunning.compareAndSet(false, true)) {
      coroutineScope.launch { clearChunksCacheDirInternal() }
    }
  }

  private suspend fun clearChunksCacheDirInternal() {
    try {
      logcat(TAG) { "clearChunksCacheDirInternal() start" }

      cacheHandlerSynchronizer.withGlobalLock {
        if (chunksCacheDirFile.exists()) {
          chunksCacheDirFile.listFiles()?.forEach { file -> file.delete() }
        }
      }

      logcat(TAG) { "clearChunksCacheDirInternal() end" }
    } finally {
      trimChunksRunning.set(false)
    }
  }

  private fun backgroundRecalculateSize() {
    if (recalculationRunning.get()) {
      return
    }

    coroutineScope.launch {
      recalculateSize()
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun recalculateSize() {
    var calculatedSize: Long = 0

    if (!recalculationRunning.compareAndSet(false, true)) {
      return
    }

    logcat(TAG) { "recalculateSize() start" }

    val time = measureTime {
      synchronized(filesOnDiskCache) { filesOnDiskCache.clear() }

      try {
        cacheHandlerSynchronizer.withGlobalLock {
          val files = cacheDirFile.listFiles() ?: emptyArray()
          for (file in files) {
            if (file.name.endsWith(CACHE_META_EXTENSION)) {
              continue
            }

            calculatedSize += file.length()
            val fileId = FileId.fromFile(file)

            synchronized(filesOnDiskCache) { filesOnDiskCache.add(fileId) }
          }
        }

        size.set(calculatedSize)
      } finally {
        recalculationRunning.set(false)
      }
    }

    logcat(TAG) {
      "recalculateSize() end took $time, " +
        "filesOnDiskCount=${filesOnDiskCache.size}, " +
        "fullyDownloadedFilesCount=${fullyDownloadedFiles.size}"
    }
  }

  private suspend fun trim() {
    BackgroundUtils.ensureBackgroundThread()

    val directoryFiles = cacheHandlerSynchronizer.withGlobalLock { cacheDirFile.listFiles() ?: emptyArray() }
    if (directoryFiles.size <= MIN_FILES_IN_DIRECTORY_FOR_TRIM_TO_START) {
      logcat(TAG) { "trim() too few files to start trim: ${directoryFiles.size}, needed: $MIN_FILES_IN_DIRECTORY_FOR_TRIM_TO_START" }
      return
    }

    val start = System.currentTimeMillis()

    // LastModified doesn't work on some platforms/phones
    // (https://issuetracker.google.com/issues/36930892)
    // so we have to use a workaround. When creating a cache file for a download we also create a
    // meta file where we will put some info about this download: the main file creation time and
    // a flag that will tell us whether the download is complete or not. So now we need to parse
    // the creation time from the meta file to sort cache files in ascending order (from the
    // oldest cache file to the newest).

    var totalDeleted = 0L
    var filesDeleted = 0

    val sortedFiles = groupFilterAndSortFiles(directoryFiles)
    val now = System.currentTimeMillis()

    val currentCacheSizeToUse = if (size.get() > fileCacheDiskSizeBytes) {
      size.get()
    } else {
      fileCacheDiskSizeBytes
    }

    val sizeDiff = (size.get() - fileCacheDiskSizeBytes).coerceAtLeast(0)
    val calculatedSizeToFree = (currentCacheSizeToUse / (100f / diskCacheCleanupRemovePercent.toFloat())).toLong()
    val sizeToFree = sizeDiff + calculatedSizeToFree

    logcat(TAG) {
      "trim() started, " +
      "cacheFileType=${cacheFileType}, " +
      "directoryFilesCount=${directoryFiles.size}, " +
      "currentCacheSize=${size.get().asReadableFileSize()}, " +
      "fileCacheDiskSizeBytes=${fileCacheDiskSizeBytes.asReadableFileSize()}, " +
      "sizeToFree=${sizeToFree.asReadableFileSize()}"
    }

    // We either delete all files we can in the cache directory or at most half of the cache
    for (cacheFile in sortedFiles) {
      val file = cacheFile.file
      val createdOn = cacheFile.createdOn

      if (now - createdOn < MIN_CACHE_FILE_LIFE_TIME) {
        val timeDelta = (now - createdOn)
        logcat(TAG) { "Skipping ${cacheFile.file.name} because it was created not long ago, timeDelta: $timeDelta" }
        break
      }

      if (totalDeleted >= sizeToFree) {
        break
      }

      val fileSize = file.length()

      if (deleteCacheFile(file)) {
        totalDeleted += fileSize
        ++filesDeleted
      }

      if (System.currentTimeMillis() - start > MAX_TRIM_TIME_MS) {
        logcat(TAG) { "Exiting trim() early, the time bound exceeded" }
        break
      }
    }

    val timeDiff = System.currentTimeMillis() - start
    recalculateSize()

    logcat(TAG) {
      "trim() ended (took ${timeDiff} ms), " +
        "cacheFileType=$cacheFileType, filesDeleted=$filesDeleted, " +
        "total space freed=${totalDeleted.asReadableFileSize()}"
    }
  }

  private suspend fun groupFilterAndSortFiles(directoryFiles: Array<File>): List<CacheFile> {
    BackgroundUtils.ensureBackgroundThread()

    val groupedCacheFiles = filterAndGroupCacheFilesWithMeta(directoryFiles)
    val cacheFiles = ArrayList<CacheFile>(groupedCacheFiles.size)

    for ((cacheFile, cacheMetaFile) in groupedCacheFiles) {
      val cacheFileMeta = try {
        readCacheFileMeta(cacheMetaFile)

      } catch (error: IOException) {
        null
      }

      if (cacheFileMeta == null) {
        logcat(TAG) { "Couldn't read cache meta for file = ${cacheFile.absolutePath}" }

        if (!deleteCacheFile(cacheFile)) {
          logcat(TAG) { "Couldn't delete cache file with meta for file = ${cacheFile.absolutePath}" }
        }
        continue
      }

      cacheFiles.add(CacheFile(file = cacheFile, cacheFileMeta = cacheFileMeta))
    }

    // Sort in ascending order, the oldest files are in the beginning of the list
    Collections.sort(cacheFiles, CACHE_FILE_COMPARATOR)
    return cacheFiles
  }

  private suspend fun filterAndGroupCacheFilesWithMeta(
    directoryFiles: Array<File>
  ): List<GroupedCacheFile> {
    BackgroundUtils.ensureBackgroundThread()

    val grouped = directoryFiles
      .filter { file ->
        val fileName = file.name

        // Either cache file or cache meta
        return@filter fileName.endsWith(CACHE_EXTENSION) || fileName.endsWith(CACHE_META_EXTENSION)
      }
      .groupBy { file -> file.name.removeExtensionFromFileName() }

    val groupedCacheFileList = mutableListWithCap<GroupedCacheFile>(grouped.size / 2)

    for ((fileName, groupOfFiles) in grouped) {
      // We have already filtered all non-cache related files so it's safe to delete them here.
      // We delete files without where either the cache file or cache file meta (or both) are
      // missing.
      if (groupOfFiles.isEmpty()) {
        deleteCacheFile(FileId.fromFileName(fileName))
        continue
      }

      // We also handle a hypothetical case where there are more than one cache file/meta with
      // the same name
      if (groupOfFiles.size != 2) {
        groupOfFiles.forEach { file -> deleteCacheFile(file) }
        continue
      }

      val file1 = groupOfFiles[0]
      val file2 = groupOfFiles[1]

      val fileName1 = file1.name
      val fileName2 = file2.name

      val cacheFile = when {
        fileName1.endsWith(CACHE_EXTENSION) -> file1
        fileName2.endsWith(CACHE_EXTENSION) -> file2
        else -> throw IllegalStateException(
          "Neither of grouped files is a cache file! " +
            "fileName1 = $fileName1, fileName2 = $fileName2"
        )
      }

      val cacheFileMeta = when {
        fileName1.endsWith(CACHE_META_EXTENSION) -> file1
        fileName2.endsWith(CACHE_META_EXTENSION) -> file2
        else -> throw IllegalStateException(
          "Neither of grouped files is a cache file meta! " +
            "fileName1 = $fileName1, fileName2 = $fileName2"
        )
      }

      groupedCacheFileList += GroupedCacheFile(
        cacheFile = cacheFile,
        cacheFileMeta = cacheFileMeta
      )
    }

    return groupedCacheFileList
  }

  private class CacheFile(
    val file: File,
    private val cacheFileMeta: CacheFileMeta
  ) {

    val createdOn: Long
      get() = cacheFileMeta.createdOn

    override fun toString(): String {
      return "CacheFile{" +
        "file=${file.absolutePath}" +
        ", cacheFileMeta=${cacheFileMeta}" +
        "}"
    }

  }

  internal class CacheFileMeta(
    val version: Int = CURRENT_META_FILE_VERSION,
    val createdOn: Long,
    val isDownloaded: Boolean
  ) {

    override fun toString(): String {
      return "CacheFileMeta{" +
        "createdOn=${formatter.print(createdOn)}" +
        ", downloaded=$isDownloaded" +
        '}'
    }

    companion object {
      const val PARTS_COUNT = 3

      private val formatter = DateTimeFormatterBuilder()
        .append(ISODateTimeFormat.date())
        .appendLiteral(' ')
        .append(ISODateTimeFormat.hourMinuteSecond())
        .appendTimeZoneOffset(null, true, 2, 2)
        .toFormatter()
    }
  }

  private data class GroupedCacheFile(
    val cacheFile: File,
    val cacheFileMeta: File
  )

  companion object {
    private const val CURRENT_META_FILE_VERSION = 1
    private const val CACHE_FILE_META_HEADER_SIZE = 4
    private const val MAX_TRIM_TIME_MS = 3000L

    // I don't think it will ever get this big but just in case don't forget to update it if it
    // ever gets
    private const val MAX_CACHE_META_SIZE = 1024L
    // We multiple by 2 explicitly to show that we want to account both cache and cache meta files.
    private const val MIN_FILES_IN_DIRECTORY_FOR_TRIM_TO_START = 8 * 2

    private const val CACHE_FILE_NAME_FORMAT = "%s.%s"
    private const val CHUNK_CACHE_FILE_NAME_FORMAT = "%s_%d_%d.%s"

    private const val CACHE_FILE_META_CONTENT_FORMAT = "%d,%d,%b"
    internal const val CACHE_EXTENSION = "cache"
    internal const val CACHE_META_EXTENSION = "cache_meta"
    internal const val CHUNK_CACHE_EXTENSION = "chunk"

    private val MIN_CACHE_FILE_LIFE_TIME = TimeUnit.MINUTES.toMillis(1)
    private val MIN_TRIM_INTERVAL = TimeUnit.SECONDS.toMillis(5)

    private val CACHE_FILE_COMPARATOR = Comparator<CacheFile> { cacheFile1, cacheFile2 ->
      cacheFile1.createdOn.compareTo(cacheFile2.createdOn)
    }
  }

}