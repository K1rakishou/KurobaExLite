package com.github.k1rakishou.kurobaexlite.ui.screens.media

import coil.disk.DiskCache
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.abortQuietly
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.suspendCall
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.internal.closeQuietly
import okio.Path

class MediaViewerScreenViewModel(
  private val chanCache: ChanCache,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val diskCache: DiskCache
) : BaseViewModel() {

  suspend fun init(mediaViewerParams: MediaViewerParams): InitResult {
    return withContext(Dispatchers.Default) {
      val initialImageUrl = mediaViewerParams.initialImage
      val imagesToShow = mutableListOf<PostImageData>()

      when (mediaViewerParams) {
        is MediaViewerParams.Catalog -> {
          val catalogThreads = chanCache.getCatalogThreads(mediaViewerParams.catalogDescriptor)

          for (catalogThread in catalogThreads) {
            val threadImages = catalogThread.images ?: continue
            if (threadImages.isNotEmpty()) {
              imagesToShow += threadImages
            }
          }
        }
        is MediaViewerParams.Thread -> {
          val threadPosts = chanCache.getThreadPosts(mediaViewerParams.threadDescriptor)

          for (threadPost in threadPosts) {
            val threadImages = threadPost.images ?: continue
            if (threadImages.isNotEmpty()) {
              imagesToShow += threadImages
            }
          }
        }
        is MediaViewerParams.Images -> TODO()
      }

      var initialPage = imagesToShow.indexOfFirst { it.fullImageUrl == initialImageUrl }
      if (initialPage < 0) {
        logcatError { "Failed to find post image with url: \'${initialImageUrl}\', resetting it" }
        initialPage = 0
      }

      return@withContext InitResult(
        images = imagesToShow.map { postImageData -> ImageLoadState.Loading(postImageData) },
        initialPage = initialPage
      )
    }
  }

  suspend fun loadFullImageAndGetFile(postImageData: PostImageData): ImageLoadState {
    val fullImageUrl = postImageData.fullImageUrl

    val loadImageResult = withContext(Dispatchers.IO) {
      Result.Try { loadFullImageInternal(postImageData) }
    }

    if (loadImageResult.isSuccess) {
      val resultFilePath = loadImageResult.getOrThrow()
      logcat(tag = TAG) { "LoadFullImage() Successfully loaded \'$fullImageUrl\'" }

      return ImageLoadState.Ready(postImageData, resultFilePath.toFile())
    } else {
      val error = loadImageResult.exceptionOrNull()!!
      logcatError(tag = TAG) { "LoadFullImage() Failed to load \'$fullImageUrl\', error=${error.asLog()}" }

      return ImageLoadState.Error(postImageData, error)
    }
  }

  private suspend fun loadFullImageInternal(postImageData: PostImageData): Path {
    BackgroundUtils.ensureBackgroundThread()

    val fullImageUrl = postImageData.fullImageUrl
    val diskCacheKey = fullImageUrl.toString()

    val snapshot = diskCache.get(diskCacheKey)
    if (snapshot != null) {
      val path = snapshot.data
      snapshot.closeQuietly()

      logcat(tag = TAG) { "loadFullImage($fullImageUrl) got image from disk cache ($path)" }
      return path
    }

    val request = Request.Builder()
      .url(fullImageUrl)
      .get()
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val responseBody = response.body
      ?: throw EmptyBodyResponseException()

    val editor = diskCache.edit(diskCacheKey)
      ?: throw ImageLoadException(fullImageUrl, "Failed to edit disk cache")

    try {
      diskCache.fileSystem.write(editor.data) {
        responseBody.source().readAll(this)
      }

      val path = editor.commitAndGet()?.data
        ?: throw ImageLoadException(fullImageUrl, "Failed to store in disk cache")

      logcat(tag = TAG) { "loadFullImage($fullImageUrl) got image from network ($path)" }
      return path
    } catch (e: Exception) {
      editor.abortQuietly()
      throw e
    }
  }

  class InitResult(
    val images: List<ImageLoadState>,
    val initialPage: Int
  )

  class ImageLoadException : ClientException {
    constructor(url: HttpUrl) : super("Failed to load image \'$url\'")
    constructor(url: HttpUrl, cause: Throwable) : super("Failed to load image \'$url\'", cause)
    constructor(url: HttpUrl, message: String) : super("Failed to load image \'$url\', reason: $message")
  }

  companion object {
    private const val TAG = "MediaViewerScreenViewModel"
  }

}