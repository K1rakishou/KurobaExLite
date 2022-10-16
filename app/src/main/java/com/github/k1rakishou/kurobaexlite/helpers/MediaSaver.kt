package com.github.k1rakishou.kurobaexlite.helpers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.SerializedCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.retryableIoTask
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.helpers.util.useBufferedSink
import com.github.k1rakishou.kurobaexlite.helpers.util.useBufferedSource
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.imageNameForDiskStore
import com.github.k1rakishou.kurobaexlite.model.data.mimeType
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.Request
import okio.BufferedSource
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MediaSaver(
  private val appScope: CoroutineScope,
  private val applicationContext: Context,
  private val androidHelpers: AndroidHelpers,
  private val globalConstants: GlobalConstants,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val parsedPostDataCache: ParsedPostDataCache,
) {
  private val activeDownloadsMutex = Mutex()

  @GuardedBy("activeDownloads")
  private val activeDownloads = mutableMapOf<String, ActiveDownload>()

  private val _activeDownloadsInfoFlow = MutableSharedFlow<String?>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val activeDownloadsInfoFlow: SharedFlow<String?>
    get() = _activeDownloadsInfoFlow.asSharedFlow()

  private val batchDownloadExecutor = SerializedCoroutineExecutor(appScope)

  suspend fun getActiveDownloadsCopy(): List<ActiveDownload> {
    return activeDownloadsMutex.withLock { activeDownloads.values.map { it.copy() } }
  }

  suspend fun activeDownloadsCount(): Int {
    return activeDownloadsMutex.withLock { activeDownloads.values.count { activeDownload -> !activeDownload.isCanceled } }
  }

  suspend fun cancelTheOnlyDownload(): Boolean {
    logcat(TAG) { "cancelTheOnlyDownload()" }

    val canceled = activeDownloadsMutex.withLock {
      if (activeDownloads.size != 1) {
        return@withLock false
      }

      val activeDownload = activeDownloads.values.firstOrNull()
        ?: return@withLock false

      activeDownload.cancel()

      return@withLock true
    }

    if (canceled) {
      notifyListeners()
    }

    return canceled
  }

  suspend fun cancelDownloadByUuid(uuid: String) {
    logcat(TAG) { "cancelDownloadByUuid($uuid)" }

    activeDownloadsMutex.withLock {
      activeDownloads[uuid]?.cancel()
    }

    notifyListeners()
  }

  suspend fun savePostImages(
    chanDescriptor: ChanDescriptor,
    postImages: List<IPostImage>
  ): ActiveDownload? {
    val completableDeferred = CompletableDeferred<ActiveDownload?>()

    logcat { "savePostImages(${chanDescriptor}) enqueueing save request of ${postImages.size} images" }

    val uuid = generateNewUuid()
    val downloadedImages = AtomicInteger(0)
    val failedImages = AtomicInteger(0)

    activeDownloadsMutex.withLock {
      activeDownloads[uuid] = ActiveDownload(
        uuid = uuid,
        chanDescriptor = chanDescriptor,
        downloaded = 0,
        failed = 0,
        total = postImages.size
      )
    }

    notifyListeners()

    batchDownloadExecutor.post {
      try {
        val result = savePostImagesInternal(
          uuid = uuid,
          downloadedImages = downloadedImages,
          failedImages = failedImages,
          chanDescriptor = chanDescriptor,
          postImages = postImages
        )

        completableDeferred.complete(result)
      } catch (error: Throwable) {
        completableDeferred.complete(null)
      }
    }

    return completableDeferred.await()
  }

  suspend fun savePostImage(postImage: IPostImage): Result<Unit> {
    val uuid = UUID.randomUUID().toString()

    return try {
      logcat { "savePostImage() start saving '${postImage.fullImageAsString}' image" }

      activeDownloadsMutex.withLock {
        activeDownloads[uuid] = ActiveDownload(
          uuid = uuid,
          chanDescriptor = postImage.ownerPostDescriptor.threadDescriptor,
          downloaded = 0,
          failed = 0,
          total = 1
        )
      }

      notifyListeners()

      savePostImageInternal(
        uuid = uuid,
        chanDescriptor = postImage.ownerPostDescriptor.threadDescriptor,
        postImage = postImage
      )
    } finally {
      logcat { "savePostImage() end saving '${postImage.fullImageAsString}' image" }

      activeDownloadsMutex.withLock { activeDownloads.remove(uuid) }
      notifyListeners()
    }
  }

  private suspend fun savePostImagesInternal(
    uuid: String,
    downloadedImages: AtomicInteger,
    failedImages: AtomicInteger,
    chanDescriptor: ChanDescriptor,
    postImages: List<IPostImage>
  ) : ActiveDownload {
    return supervisorScope {
      try {
        logcat { "savePostImagesInternal(${chanDescriptor}) start saving ${postImages.size} images" }

        postImages
          .chunked(globalConstants.coresCount)
          .forEach { batch ->
            val isCanceled = activeDownloadsMutex.withLock { activeDownloads[uuid]?.isCanceled ?: true }
            if (isCanceled) {
              batch.forEach { _ -> failedImages.incrementAndGet() }
            } else {
              val results = batch
                .map { postImage -> async { savePostImageInternal(uuid, chanDescriptor, postImage) } }
                .awaitAll()

              results.forEach { downloadResult ->
                if (downloadResult.isSuccess) {
                  downloadedImages.incrementAndGet()
                } else {
                  failedImages.incrementAndGet()
                }
              }
            }

            activeDownloads[uuid]?.let { activeDownload ->
              activeDownload.downloaded = downloadedImages.get()
              activeDownload.failed = failedImages.get()
            }

            notifyListeners()
          }

        return@supervisorScope ActiveDownload(
          uuid = uuid,
          chanDescriptor = chanDescriptor,
          downloaded = downloadedImages.get(),
          failed = failedImages.get(),
          total = postImages.size
        )
      } finally {
        logcat { "savePostImagesInternal(${chanDescriptor}) end saving ${postImages.size} images" }

        activeDownloadsMutex.withLock { activeDownloads.remove(uuid) }
        notifyListeners()
      }
    }
  }

  private suspend fun savePostImageInternal(
    uuid: String,
    chanDescriptor: ChanDescriptor,
    postImage: IPostImage
  ): Result<Unit> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val request = Request.Builder()
          .url(postImage.fullImageAsUrl)
          .get()
          .build()

        retryableIoTask(
          attempts = 5,
          task = {
            val isCanceled = activeDownloadsMutex.withLock { activeDownloads[uuid]?.isCanceled ?: true }
            if (isCanceled) {
              logcat {
                "savePostImageInternal(${uuid}, ${chanDescriptor}) " +
                  "download media '${postImage.fullImageAsString}' items was canceled"
              }

              throw MediaDownloadCanceledManually(postImage)
            }

            val response = proxiedOkHttpClient.okHttpClient()
              .suspendCall(request)
              .unwrap()

            if (!response.isSuccessful) {
              if (response.code == 404) {
                throw MediaNotFoundOnServerException(postImage)
              }

              throw MediaDownloadBadStatusException(postImage, response.code)
            }

            val responseBody = response.body
              ?: throw MediaDownloadEmptyBody(postImage)

            responseBody.useBufferedSource { bufferedSource ->
              if (androidHelpers.isAndroidQ()) {
                savePostImageAndroidQAndAbove(postImage, bufferedSource)
              } else {
                savePostImageAndroidPAndBelow(postImage, bufferedSource)
              }
            }
          }
        )
      }.onFailure { error ->
        logcatError(TAG) {
          "savePostImageInternal(${uuid}, ${chanDescriptor}) " +
            "'${postImage.fullImageAsString}'error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }.onSuccess {
        logcat(TAG, LogPriority.VERBOSE) {
          "savePostImageInternal(${uuid}, ${chanDescriptor}) " +
            "'${postImage.fullImageAsString}' success"
        }
      }
    }
  }

  @Suppress("DEPRECATION")
  private suspend fun savePostImageAndroidPAndBelow(postImage: IPostImage, bufferedSource: BufferedSource) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val outputDir = File(downloadsDir, getFullDir(postImage, APP_FILES_DIR))

    if (!outputDir.exists()) {
      check(outputDir.mkdirs()) { "\'${outputDir.absolutePath}\' mkdirs() failed" }
    }

    var outputFile = File(outputDir, postImage.imageNameForDiskStore())
    var duplicateIndex = 1

    while (true) {
      if (!outputFile.exists()) {
        break
      }

      outputFile = File(outputDir, postImage.imageNameForDiskStore(duplicateIndex))
      ++duplicateIndex
    }

    outputFile.outputStream().useBufferedSink { bufferedSink ->
      bufferedSink.writeAll(bufferedSource)
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private suspend fun savePostImageAndroidQAndAbove(postImage: IPostImage, bufferedSource: BufferedSource) {
    val contentResolver = applicationContext.contentResolver
    val outputDir = "${Environment.DIRECTORY_DOWNLOADS}/${getFullDir(postImage, APP_FILES_DIR)}"
    val fileName = postImage.imageNameForDiskStore()

    val contentValues = ContentValues()
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, postImage.mimeType())
    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, outputDir)

    val uri = contentResolver.insert(
      MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
      contentValues
    ) ?: throw MediaSaveException(postImage, "contentResolver.insert() returned null")

    val stream = contentResolver.openOutputStream(uri)
      ?: throw MediaSaveException(postImage, "contentResolver.openOutputStream() returned null")

    stream.useBufferedSink { bufferedSink ->
      bufferedSink.writeAll(bufferedSource)
    }
  }

  private suspend fun getFullDir(postImage: IPostImage, appFilesDir: String): String {
    val postDescriptor = postImage.ownerPostDescriptor
    val originalPostDescriptor = postDescriptor.threadDescriptor.toOriginalPostDescriptor()

    val threadTitle = parsedPostDataCache.getParsedPostData(originalPostDescriptor)?.parsedPostSubject
      ?.mapNotNull { char -> if (char.isLetterOrDigit()) char else '_' }
      ?.let { charList -> String(charList.toCharArray()) }

    return buildString {
      append(appFilesDir)
      append("/")
      append(postDescriptor.siteKeyActual)
      append("/")
      append(postDescriptor.boardCode)
      append("/")

      if (threadTitle.isNotNullNorBlank()) {
        append(postDescriptor.threadNo)
        append("_")
        append("(")
        append(threadTitle)
        append(")")
      } else {
        append(postDescriptor.threadNo)
      }
    }
  }

  private suspend fun generateNewUuid(): String {
    while (true) {
      val uuid = UUID.randomUUID().toString()

      val keyAlreadyExists = activeDownloadsMutex.withLock { activeDownloads.containsKey(uuid) }
      if (keyAlreadyExists) {
        continue
      }

      return uuid
    }
  }

  private suspend fun notifyListeners() {
    val activeDownloadsInfo = activeDownloadsMutex.withLock {
      val nonCanceledActiveDownloads = activeDownloads.values
        .filter { activeDownload -> !activeDownload.isCanceled }

      if (nonCanceledActiveDownloads.isEmpty()) {
        return@withLock null
      }

      if (nonCanceledActiveDownloads.size == 1) {
        val activeDownload = nonCanceledActiveDownloads.firstOrNull()
          ?: return@withLock null

        return@withLock "Downloaded: ${activeDownload.downloaded}, " +
          "Failed: ${activeDownload.failed}, " +
          "Total: ${activeDownload.total}"
      } else {
        val totalDownloaded = nonCanceledActiveDownloads.sumOf { it.downloaded }
        val totalFailed = nonCanceledActiveDownloads.sumOf { it.failed }
        val total = nonCanceledActiveDownloads.sumOf { it.total }

        return@withLock "Downloaded: ${totalDownloaded}, " +
          "Failed: ${totalFailed}, " +
          "Total: ${total}, " +
          "Active downloads: ${nonCanceledActiveDownloads.size}"
      }
    }

    _activeDownloadsInfoFlow.tryEmit(activeDownloadsInfo)
  }

  data class ActiveDownload(
    val uuid: String,
    val chanDescriptor: ChanDescriptor,
    var downloaded: Int,
    var failed: Int,
    val total: Int
  ) {
    private val canceled = AtomicBoolean(false)
    val isCanceled: Boolean
      get() = canceled.get()

    fun cancel() {
      canceled.set(true)
    }

  }

  class MediaNotFoundOnServerException(postImage: IPostImage) :
    ClientException("Media file '${postImage.fullImageAsString}' not found on server (404)")

  class MediaDownloadBadStatusException(postImage: IPostImage, statusCode: Int) :
    ClientException("Failed to save media '${postImage.fullImageAsString}', bad status code (${statusCode})")

  class MediaDownloadEmptyBody(postImage: IPostImage) :
    ClientException("Failed to save media '${postImage.fullImageAsString}', empty response body")

  class MediaDownloadCanceledManually(postImage: IPostImage) :
    ClientException("Media download '${postImage.fullImageAsString}' was canceled by the user")

  class MediaSaveException : ClientException {
    constructor(postImage: IPostImage) :
      super("Failed to save media \'${postImage.fullImageAsString}\' on disk")
    constructor(postImage: IPostImage, cause: Throwable) :
      super("Failed to save media \'${postImage.fullImageAsString}\' on disk", cause)
    constructor(postImage: IPostImage, message: String) :
      super("Failed to save media \'${postImage.fullImageAsString}\' on disk, reason: $message")
  }

  companion object {
    private const val TAG = "MediaSaver"
    private const val APP_FILES_DIR = "KurobaExLite"
  }

}