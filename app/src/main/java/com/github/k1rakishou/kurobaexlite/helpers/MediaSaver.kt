package com.github.k1rakishou.kurobaexlite.helpers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.imageNameForDiskStore
import com.github.k1rakishou.kurobaexlite.model.data.mimeType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.BufferedSource

class MediaSaver(
  private val applicationContext: Context,
  private val androidHelpers: AndroidHelpers,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {

  suspend fun savePostImage(postImage: IPostImage): Result<Unit> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val request = Request.Builder()
          .url(postImage.fullImageAsUrl)
          .get()
          .build()

        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)

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
    }
  }

  @Suppress("DEPRECATION")
  private suspend fun savePostImageAndroidPAndBelow(postImage: IPostImage, bufferedSource: BufferedSource) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val kurobaExDir = File(downloadsDir, APP_FILES_DIR)

    if (!kurobaExDir.exists()) {
      check(kurobaExDir.mkdir()) { "\'${kurobaExDir.absolutePath}\' mkdir() failed" }
    }

    var outputFile = File(kurobaExDir, postImage.imageNameForDiskStore())
    var duplicateIndex = 1

    while (true) {
      if (!outputFile.exists()) {
        break
      }

      outputFile = File(kurobaExDir, postImage.imageNameForDiskStore(duplicateIndex))
      ++duplicateIndex
    }

    outputFile.outputStream().useBufferedSink { bufferedSink ->
      bufferedSink.writeAll(bufferedSource)
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private suspend fun savePostImageAndroidQAndAbove(postImage: IPostImage, bufferedSource: BufferedSource) {
    val contentResolver = applicationContext.contentResolver

    var fileName = postImage.imageNameForDiskStore()
    var duplicateIndex = 1

    while (true) {
      if (!fileExists(fileName)) {
        break
      }

      fileName = postImage.imageNameForDiskStore(duplicateIndex)
      ++duplicateIndex
    }

    val contentValues = ContentValues()
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, postImage.mimeType())
    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$APP_FILES_DIR")

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

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun fileExists(fileName: String): Boolean {
    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
    val projection = arrayOf(MediaStore.Files.FileColumns._ID)

    val contentResolver = applicationContext.contentResolver
    val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    val cursor = contentResolver.query(contentUri, projection, selection, arrayOf(fileName), null)
      ?: return false

    return cursor.use { c -> c.moveToFirst() }
  }

  class MediaNotFoundOnServerException(postImage: IPostImage) :
    ClientException("Media file '${postImage.fullImageAsString}' not found on server (404)")

  class MediaDownloadBadStatusException(postImage: IPostImage, statusCode: Int) :
    ClientException("Failed to save media '${postImage.fullImageAsString}', bad status code (${statusCode})")

  class MediaDownloadEmptyBody(postImage: IPostImage) :
    ClientException("Failed to save media '${postImage.fullImageAsString}', empty response body")

  class MediaSaveException : ClientException {
    constructor(postImage: IPostImage) : super("Failed to save media \'${postImage.fullImageAsString}\' on disk")
    constructor(postImage: IPostImage, cause: Throwable) : super("Failed to save media \'${postImage.fullImageAsString}\' on disk", cause)
    constructor(postImage: IPostImage, message: String) : super("Failed to save media \'${postImage.fullImageAsString}\' on disk, reason: $message")
  }

  companion object {
    private const val APP_FILES_DIR = "KurobaExLite"
  }

}