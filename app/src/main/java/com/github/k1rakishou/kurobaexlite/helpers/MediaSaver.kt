package com.github.k1rakishou.kurobaexlite.helpers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.imageNameForDiskStore
import com.github.k1rakishou.kurobaexlite.model.data.mimeType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.Request
import okio.BufferedSource

class MediaSaver(
  private val applicationContext: Context,
  private val androidHelpers: AndroidHelpers,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val parsedPostDataCache: ParsedPostDataCache,
) {

  suspend fun savePostImage(postImage: IPostImage): Result<Unit> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val request = Request.Builder()
          .url(postImage.fullImageAsUrl)
          .get()
          .build()

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
      }.onFailure { error ->
        logcatError(TAG) { "savePostImage(${postImage.fullImageAsString}) error: ${error.asLogIfImportantOrErrorMessage()}" }
      }.onSuccess {
        logcat(TAG, LogPriority.VERBOSE) { "savePostImage(${postImage.fullImageAsString}) success" }
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

  class MediaNotFoundOnServerException(postImage: IPostImage) :
    ClientException("Media file '${postImage.fullImageAsString}' not found on server (404)")

  class MediaDownloadBadStatusException(postImage: IPostImage, statusCode: Int) :
    ClientException("Failed to save media '${postImage.fullImageAsString}', bad status code (${statusCode})")

  class MediaDownloadEmptyBody(postImage: IPostImage) :
    ClientException("Failed to save media '${postImage.fullImageAsString}', empty response body")

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