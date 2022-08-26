package com.github.k1rakishou.kurobaexlite.helpers.picker

import android.content.Context
import com.github.k1rakishou.kurobaexlite.features.reply.AttachedMedia
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.executors.SerializedCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.extractFileNameExtension
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.suspendCall
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.internal.closeQuietly
import okio.sink

class RemoteFilePicker(
  appContext: Context,
  private val appScope: CoroutineScope,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
) : AbstractFilePicker(appContext) {
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  suspend fun pickFile(
    chanDescriptor: ChanDescriptor,
    imageUrls: List<String>,
    showLoadingView: suspend () -> Unit,
    hideLoadingView: suspend () -> Unit
  ): Result<AttachedMedia> {
    return Result.runCatching {
      pickFileInternal(
        chanDescriptor = chanDescriptor,
        imageUrls = imageUrls,
        showLoadingView = showLoadingView,
        hideLoadingView = hideLoadingView
      )
    }
  }

  private suspend fun pickFileInternal(
    chanDescriptor: ChanDescriptor,
    imageUrls: List<String>,
    showLoadingView: suspend () -> Unit,
    hideLoadingView: suspend () -> Unit
  ): AttachedMedia {
    if (imageUrls.isEmpty()) {
      throw PickFileError("No url")
    }

    return withContext(Dispatchers.IO) {
      var downloadedFileMaybe: File? = null
      var lastError: Throwable? = null
      var downloadedUrl: HttpUrl? = null
      var resultFileName: String? = null

      serializedCoroutineExecutor.post { showLoadingView.invoke() }

      try {
        // Download the first image out of the provided urls because some of them may fail
        for (imageUrlRaw in imageUrls) {
          downloadedUrl = imageUrlRaw.toHttpUrlOrNull()
          if (downloadedUrl == null) {
            lastError = PickFileError("Bad url: \'${imageUrlRaw}\'")
            continue
          }

          val fileName = getRemoteFileName(downloadedUrl)

          val downloadFileResult = Result.runCatching { downloadFile(chanDescriptor, downloadedUrl, fileName) }
          if (downloadFileResult.isFailure) {
            lastError = downloadFileResult.exceptionOrThrow()
            continue
          }

          downloadedFileMaybe = downloadFileResult.getOrNull()
          resultFileName = fileName

          break
        }
      } finally {
        serializedCoroutineExecutor.post { hideLoadingView.invoke() }
      }

      if (downloadedFileMaybe == null || downloadedUrl == null || resultFileName == null) {
        if (lastError != null) {
          throw lastError
        }

        throw PickFileError("Failed to pick remote file at \'${imageUrls}\' for unknown reason")
      }

      return@withContext AttachedMedia(
        path = downloadedFileMaybe.absolutePath,
        fileName = resultFileName
      )
    }
  }

  private suspend fun downloadFile(
    chanDescriptor: ChanDescriptor,
    imageUrl: HttpUrl,
    fileName: String
  ): File {
    val request = Request.Builder()
      .url(imageUrl)
      .get()
      .build()

    val responseResult = proxiedOkHttpClient.okHttpClient()
      .suspendCall(request)

    if (responseResult.isFailure) {
      throw responseResult.exceptionOrThrow()
    }

    return responseResult.getOrThrow().use { response ->
      return@use response.body.use { responseBody ->
        if (responseBody == null) {
          throw EmptyBodyResponseException()
        }

        if (!attachedMediaDir.exists()) {
          check(attachedMediaDir.mkdirs()) { "Failed to create \'${attachedMediaDir.path}\' directory" }
        }

        val extension = fileName.extractFileNameExtension()

        val diskFile = createAttachMediaFile(
          chanDescriptor = chanDescriptor,
          attachedMediaDir = attachedMediaDir,
          extension = extension
        )

        if (diskFile == null) {
          throw PickFileError("Failed to create cache file to download image into")
        }

        try {
          runInterruptible {
            responseBody.source().use { source ->
              diskFile.outputStream().sink().use { sink ->
                source.readAll(sink)
              }
            }
          }

          return@use diskFile
        } catch (error: Throwable) {
          diskFile.delete()
          throw error
        } finally {
          responseBody.closeQuietly()
        }
      }
    }
  }

}