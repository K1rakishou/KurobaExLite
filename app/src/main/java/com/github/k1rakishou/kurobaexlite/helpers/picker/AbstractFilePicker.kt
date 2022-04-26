package com.github.k1rakishou.kurobaexlite.helpers.picker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.github.k1rakishou.kurobaexlite.features.reply.AttachedMedia
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException

abstract class AbstractFilePicker(
  private val appContext: Context
) {
  protected val attachedMediaDir = File(appContext.cacheDir, "attached_media")

  abstract suspend fun pickFile(
    chanDescriptor: ChanDescriptor,
    allowMultiSelection: Boolean
  ): Result<List<AttachedMedia>>

  protected fun copyExternalFileToReplyFileStorage(
    chanDescriptor: ChanDescriptor,
    externalFileUri: Uri,
  ): Result<AttachedMedia> {
    BackgroundUtils.ensureBackgroundThread()

    return runCatching {
      if (!attachedMediaDir.exists()) {
        check(attachedMediaDir.mkdirs()) { "Failed to create \'${attachedMediaDir.path}\' directory" }
      }

      val replyFile = createAttachMediaFile(chanDescriptor, attachedMediaDir)
      val originalFileName = tryExtractFileNameOrDefault(externalFileUri, appContext)

      if (replyFile == null) {
        throw IOException("Failed to get attach file")
      }

      try {
        copyExternalFileIntoReplyFile(appContext, externalFileUri, replyFile)
      } catch (error: Throwable) {
        replyFile.delete()
        throw error
      }

      return@runCatching AttachedMedia(
        path = replyFile.absolutePath,
        fileName = originalFileName
      )
    }
  }

  private fun createAttachMediaFile(
    chanDescriptor: ChanDescriptor,
    attachedMediaDir: File
  ): File? {
    val dirName = when (chanDescriptor) {
      is CatalogDescriptor -> "${chanDescriptor.siteKeyActual}_${chanDescriptor.boardCode}"
      is ThreadDescriptor -> "${chanDescriptor.siteKeyActual}_${chanDescriptor.boardCode}_${chanDescriptor.threadNo}"
    }

    val thisReplyAttachMediaDir = File(attachedMediaDir, dirName)
    if (!thisReplyAttachMediaDir.exists()) {
      check(thisReplyAttachMediaDir.mkdirs()) { "Failed to create \'${thisReplyAttachMediaDir.path}\' directory" }
    }

    val resultFile = File(thisReplyAttachMediaDir, System.currentTimeMillis().toString())
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

  private fun tryExtractFileNameOrDefault(uri: Uri, appContext: Context): String {
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

  private fun getRandomFileNameWithoutExtension(): String {
    return System.currentTimeMillis().toString()
  }

  private fun copyExternalFileIntoReplyFile(
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

  companion object {
    private const val TAG = "AbstractFilePicker"
  }

}