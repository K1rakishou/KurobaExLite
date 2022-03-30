package com.github.k1rakishou.kurobaexlite.helpers.cache

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import logcat.logcat
import okhttp3.HttpUrl

@OptIn(ExperimentalTime::class)
class KurobaLruDiskCache(
  private val appContext: Context,
  private val diskCacheDir: File,
  private val androidHelpers: AndroidHelpers,
  private val totalFileCacheDiskSizeBytes: Long
) {
  private val innerCaches = ConcurrentHashMap<CacheFileType, KurobaInnerLruDiskCache>()

  init {
    val duration = measureTime { init() }
    logcat(tag = TAG) { "CacheHandler.init() took $duration" }
  }

  private fun init() {
    if (androidHelpers.isDevFlavor()) {
      CacheFileType.checkValid()
    }

    if (!diskCacheDir.exists()) {
      diskCacheDir.mkdirs()
    }

    logcat(TAG) {
      "diskCacheDir=${diskCacheDir.absolutePath}, " +
        "totalFileCacheDiskSize=${totalFileCacheDiskSizeBytes.asReadableFileSize()}"
    }

    for (cacheFileType in CacheFileType.values()) {
      if (innerCaches.containsKey(cacheFileType)) {
        continue
      }

      val innerCacheDirFile = File(File(diskCacheDir, cacheFileType.id.toString()), "files")
      if (!innerCacheDirFile.exists()) {
        innerCacheDirFile.mkdirs()
      }

      val innerCacheChunksDirFile = File(File(diskCacheDir, cacheFileType.id.toString()), "chunks")
      if (!innerCacheChunksDirFile.exists()) {
        innerCacheChunksDirFile.mkdirs()
      }

      val innerCache = KurobaInnerLruDiskCache(
        appContext = appContext,
        androidHelpers = androidHelpers,
        sharedDispatcher = Dispatchers.IO,
        cacheDirFile = innerCacheDirFile,
        chunksCacheDirFile = innerCacheChunksDirFile,
        fileCacheDiskSizeBytes = cacheFileType.calculateDiskSize(totalFileCacheDiskSizeBytes),
        cacheFileType = cacheFileType,
        isDevBuild = ENABLE_LOGGING
      )

      innerCaches.put(cacheFileType, innerCache)
    }
  }

  suspend fun getCacheFileOrNull(cacheFileType: CacheFileType, url: HttpUrl): File? {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "getCacheFileOrNull($cacheFileType, $url) start" }
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)
    val file = innerCache.getCacheFileOrNull(url)

    if (ENABLE_LOGGING) {
      logcat(TAG) { "getCacheFileOrNull($cacheFileType, $url) end -> ${file?.name}" }
    }

    return file
  }

  /**
   * Either returns already downloaded file or creates an empty new one on the disk (also creates
   * cache file meta with default parameters)
   * */
  suspend fun getOrCreateCacheFile(cacheFileType: CacheFileType, url: HttpUrl): File? {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "getOrCreateCacheFile($cacheFileType, $url) start" }
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)
    val file = innerCache.getOrCreateCacheFile(url)

    if (ENABLE_LOGGING) {
      logcat(TAG) { "getOrCreateCacheFile($cacheFileType, $url) end -> ${file?.name}" }
    }

    return file
  }

  suspend fun getChunkCacheFileOrNull(
    cacheFileType: CacheFileType,
    chunkStart: Long,
    chunkEnd: Long,
    url: HttpUrl
  ): File? {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "getChunkCacheFileOrNull($cacheFileType, $chunkStart..$chunkEnd, $url)" }
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)

    return innerCache.getChunkCacheFileOrNull(chunkStart, chunkEnd, url)
  }

  suspend fun getOrCreateChunkCacheFile(
    cacheFileType: CacheFileType,
    chunkStart: Long,
    chunkEnd: Long,
    url: HttpUrl
  ): File? {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "getOrCreateChunkCacheFile($cacheFileType, $chunkStart..$chunkEnd, $url)" }
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)

    return innerCache.getOrCreateChunkCacheFile(chunkStart, chunkEnd, url)
  }

  suspend fun cacheFileExistsInMemoryCache(cacheFileType: CacheFileType, url: HttpUrl): Boolean {
    val innerCache = getInnerCacheByFileType(cacheFileType)
    val exists = innerCache.cacheFileExistsInMemoryCache(url)

    if (ENABLE_LOGGING) {
      logcat(TAG) { "cacheFileExistsInMemoryCache($cacheFileType, $url) -> $exists" }
    }

    return exists
  }

  suspend fun deleteCacheFileByUrlSuspend(cacheFileType: CacheFileType, url: HttpUrl): Boolean {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "deleteCacheFileByUrlSuspend($cacheFileType, $url)" }
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)
    return innerCache.deleteCacheFile(url)
  }

  suspend fun deleteCacheFileByUrl(cacheFileType: CacheFileType, url: HttpUrl): Boolean {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "deleteCacheFileByUrl($cacheFileType, $url)" }
    }

    val innerCache = getInnerCacheByFileType(cacheFileType)

    return getInnerCacheByFileType(cacheFileType).deleteCacheFile(url)
  }

  suspend fun cacheFileExistsOnDisk(cacheFileType: CacheFileType, url: HttpUrl): Boolean {
    val innerCache = getInnerCacheByFileType(cacheFileType)
    val alreadyDownloaded = cacheFileExistsOnDisk(cacheFileType, innerCache.getCacheFileByUrl(url))

    if (ENABLE_LOGGING) {
      logcat(TAG) { "cacheFileExistsInMemoryCache($cacheFileType, $url) -> $alreadyDownloaded" }
    }

    return alreadyDownloaded
  }

  /**
   * Checks whether this file is already downloaded by reading it's meta info. If a file has no
   * meta info or it cannot be read - deletes the file so it can be re-downloaded again with all
   * necessary information
   *
   * [cacheFile] must be the cache file, not cache file meta!
   * */
  suspend fun cacheFileExistsOnDisk(cacheFileType: CacheFileType, cacheFile: File): Boolean {
    val alreadyDownloaded = getInnerCacheByFileType(cacheFileType).cacheFileExistsOnDisk(cacheFile)

    if (ENABLE_LOGGING) {
      logcat(TAG) { "cacheFileExistsOnDisk($cacheFileType, ${cacheFile.name}) -> $alreadyDownloaded" }
    }

    return alreadyDownloaded
  }

  suspend fun markFileDownloaded(cacheFileType: CacheFileType, file: File): Boolean {
    val markedAsDownloaded = getInnerCacheByFileType(cacheFileType).markFileDownloaded(file)

    if (ENABLE_LOGGING) {
      logcat(TAG) { "markFileDownloaded($cacheFileType, ${file.name}) -> $markedAsDownloaded" }
    }

    if (markedAsDownloaded) {
      val fileLen = file.length()
      val totalSize = getInnerCacheByFileType(cacheFileType).fileWasAdded(fileLen)

      if (ENABLE_LOGGING) {
        val maxSizeFormatted = getInnerCacheByFileType(cacheFileType).getMaxSize().asReadableFileSize()
        val fileLenFormatted = fileLen.asReadableFileSize()
        val totalSizeFormatted = totalSize.asReadableFileSize()

        logcat(TAG) { "fileWasAdded($cacheFileType, ${file.name}, ${fileLenFormatted}) -> (${totalSizeFormatted} / ${maxSizeFormatted})" }
      }
    }

    return markedAsDownloaded
  }

  suspend fun getSize(cacheFileType: CacheFileType): Long {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "getSize($cacheFileType)" }
    }

    return getInnerCacheByFileType(cacheFileType).getSize()
  }

  suspend fun getMaxSize(cacheFileType: CacheFileType): Long {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "getMaxSize($cacheFileType)" }
    }

    return getInnerCacheByFileType(cacheFileType).getMaxSize()
  }

  /**
   * For now only used in developer settings. Clears the cache completely.
   * */
  suspend fun clearCache(cacheFileType: CacheFileType) {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "clearCache($cacheFileType)" }
    }

    getInnerCacheByFileType(cacheFileType).clearCache()
  }

  /**
   * Deletes a cache file with it's meta. Also decreases the total cache size variable by the size
   * of the file.
   * */
  suspend fun deleteCacheFile(cacheFileType: CacheFileType, cacheFile: File): Boolean {
    if (ENABLE_LOGGING) {
      logcat(TAG) { "deleteCacheFile($cacheFileType, ${cacheFile.name})" }
    }

    return getInnerCacheByFileType(cacheFileType).deleteCacheFile(cacheFile)
  }

  private fun getInnerCacheByFileType(cacheFileType: CacheFileType): KurobaInnerLruDiskCache {
    return innerCaches[cacheFileType]!!
  }

  companion object {
    private const val TAG = "KurobaLruDiskCache"
    private const val ENABLE_LOGGING = false
  }
}