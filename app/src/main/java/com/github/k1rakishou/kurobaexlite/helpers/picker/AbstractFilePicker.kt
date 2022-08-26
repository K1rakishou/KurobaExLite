package com.github.k1rakishou.kurobaexlite.helpers.picker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.extractFileName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import okhttp3.HttpUrl

abstract class AbstractFilePicker(
  private val appContext: Context
) {
  protected val attachedMediaDir = File(appContext.cacheDir, "attached_media")

  protected fun createAttachMediaFile(
    chanDescriptor: ChanDescriptor,
    attachedMediaDir: File,
    extension: String?
  ): File? {
    val dirName = when (chanDescriptor) {
      is CatalogDescriptor -> "${chanDescriptor.siteKeyActual}_${chanDescriptor.boardCode}"
      is ThreadDescriptor -> "${chanDescriptor.siteKeyActual}_${chanDescriptor.boardCode}_${chanDescriptor.threadNo}"
    }

    val thisReplyAttachMediaDir = File(attachedMediaDir, dirName)
    if (!thisReplyAttachMediaDir.exists()) {
      check(thisReplyAttachMediaDir.mkdirs()) { "Failed to create \'${thisReplyAttachMediaDir.path}\' directory" }
    }

    val resultFileName = buildString {
      append(System.currentTimeMillis().toString())

      if (extension.isNotNullNorEmpty()) {
        append('.')
        append(extension)
      }
    }

    val resultFile = File(thisReplyAttachMediaDir, resultFileName)
    if (resultFile.exists()) {
      logcatError(TAG) { "resultFile (${resultFile.path}) already exists" }
      return null
    }

    if (!resultFile.createNewFile()) {
      logcatError(TAG) { "Failed to create resultFile (${resultFile.path})" }
      return null
    }

    return resultFile
  }

  protected fun tryExtractFileNameOrDefault(uri: Uri, appContext: Context): String {
    var fileName: String? = null

    try {
      fileName = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex > -1 && cursor.moveToFirst()) {
          return@use cursor.getString(nameIndex)
        }

        return@use null
      }
    } catch (error: Throwable) {
      logcatError(TAG) {
        "tryExtractFileNameOrDefault() contentResolver.query failed " +
          "(url='$uri', error=${error.asLogIfImportantOrErrorMessage()})"
      }
    }

    // TODO(KurobaEx):
//    if (ChanSettings.alwaysRandomizePickedFilesNames.get()) {
//      return getRandomFileName(fileName ?: uri.lastPathSegment)
//    }

    if (fileName == null) {
      // As per the comment on OpenableColumns.DISPLAY_NAME:
      // If this is not provided then the name should default to the last segment
      // of the file's URI.
      fileName = uri.lastPathSegment
        ?: getRandomFileNameWithoutExtension()
    }

    return fileName
  }

  protected fun getRemoteFileName(url: HttpUrl): String {
    val actualFileName = url.extractFileName()

//    if (ChanSettings.alwaysRandomizePickedFilesNames.get()) {
//      return getRandomFileName(actualFileName)
//    }

    return actualFileName
      ?: getRandomFileNameWithoutExtension()
  }

  private fun getRandomFileNameWithoutExtension(): String {
    return System.currentTimeMillis().toString()
  }

  protected fun copyExternalFileIntoReplyFile(
    appContext: Context,
    uri: Uri,
    replyFile: File
  ) {
    val contentResolver = appContext.contentResolver

    contentResolver.openFileDescriptor(uri, "r").use { fileDescriptor ->
      if (fileDescriptor == null) {
        throw IOException("Couldn't open file descriptor for uri = $uri")
      }

      FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
        replyFile.outputStream().use { outputStream ->
          inputStream.copyTo(outputStream)
        }
      }
    }
  }

  class PickFileError : ClientException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  companion object {
    private const val TAG = "AbstractFilePicker"
  }

}