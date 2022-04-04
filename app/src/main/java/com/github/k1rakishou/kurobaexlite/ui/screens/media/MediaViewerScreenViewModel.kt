package com.github.k1rakishou.kurobaexlite.ui.screens.media

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.mpv.MpvInitializer
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.cache.CacheFileType
import com.github.k1rakishou.kurobaexlite.helpers.cache.KurobaLruDiskCache
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.network.ProgressResponseBody
import com.github.k1rakishou.kurobaexlite.helpers.suspendCall
import com.github.k1rakishou.kurobaexlite.interactors.InstallMpvNativeLibrariesFromGithub
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.internal.closeQuietly
import okio.sink
import org.koin.java.KoinJavaComponent.inject

class MediaViewerScreenViewModel(
  private val application: KurobaExLiteApplication,
  val mpvSettings: MpvSettings,
  private val chanCache: ChanCache,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val kurobaLruDiskCache: KurobaLruDiskCache
) : BaseAndroidViewModel(application) {
  private val installMpvNativeLibrariesFromGithub: InstallMpvNativeLibrariesFromGithub by inject(InstallMpvNativeLibrariesFromGithub::class.java)
  private val mpvInitializer = MpvInitializer(appContext, mpvSettings)

  suspend fun init(mediaViewerParams: MediaViewerParams): InitResult {
    return withContext(Dispatchers.Default) {
      // Trim the cache every time we open the media viewer
      kurobaLruDiskCache.manualTrim(CacheFileType.PostMediaFull)

      val initialImageUrl = mediaViewerParams.initialImage
      val imagesToShow = mutableListOf<IPostImage>()

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
        is MediaViewerParams.Images -> {
          for (postCellImageData in mediaViewerParams.images) {
            imagesToShow += postCellImageData
          }
        }
      }

      var initialPage = imagesToShow.indexOfFirst { it.fullImageAsUrl == initialImageUrl }
      if (initialPage < 0) {
        logcatError { "Failed to find post image with url: \'${initialImageUrl}\', resetting it" }
        initialPage = 0
      }

      val hasVideos = imagesToShow.any { postImage -> postImage.imageType() == ImageType.Video }
      if (hasVideos) {
        mpvInitializer.init()
      }

      return@withContext InitResult(
        images = imagesToShow.map { postImageData -> ImageLoadState.PreparingForLoading(postImageData) },
        initialPage = initialPage
      )
    }
  }

  fun destroy() {
    mpvInitializer.destroy()
  }

  suspend fun loadFullImageAndGetFile(
    postImageData: IPostImage
  ): Flow<ImageLoadState> {
    return channelFlow {
      val fullImageUrl = postImageData.fullImageAsUrl

      val loadImageResult = withContext(Dispatchers.IO) {
        try {
          logcat { "loadFullImageInternal(${fullImageUrl}) start" }
          Result.Try { loadFullImageInternal(postImageData, this@channelFlow) }
        } finally {
          logcat { "loadFullImageInternal(${fullImageUrl}) end" }
        }
      }

      if (loadImageResult.isSuccess) {
        val resultFile = loadImageResult.getOrThrow()
        logcat(tag = TAG) { "loadFullImageAndGetFile() Successfully loaded \'$fullImageUrl\'" }
        send(ImageLoadState.Ready(postImageData, resultFile))
      } else {
        val error = loadImageResult.exceptionOrNull()!!
        logcatError(tag = TAG) { "loadFullImageAndGetFile() Failed to load \'$fullImageUrl\', error=${error.asLog()}" }

        send(ImageLoadState.Error(postImageData, error))
      }
    }
  }

  private suspend fun loadFullImageInternal(
    postImageData: IPostImage,
    producer: ProducerScope<ImageLoadState>
  ): File {
    BackgroundUtils.ensureBackgroundThread()

    val fullImageUrl = postImageData.fullImageAsUrl
    val cacheFileType = CacheFileType.PostMediaFull

    val cachedFile = kurobaLruDiskCache.getCacheFileOrNull(cacheFileType, fullImageUrl)
    if (cachedFile != null) {
      logcat(tag = TAG) { "loadFullImage($fullImageUrl) got image from disk cache (${cachedFile.absolutePath})" }
      return cachedFile
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

    val diskFile = kurobaLruDiskCache.getOrCreateCacheFile(cacheFileType, fullImageUrl)
      ?: throw ImageLoadException(fullImageUrl, "Failed to create cache file on disk")

    try {
      runInterruptible {
        val progressResponseBody = ProgressResponseBody(
          responseBody = responseBody,
          progressListener = { read, total, done ->
            var progress = read.toFloat() / total.toFloat()
            if (done) {
              progress = 1f
            }

            producer.trySend(ImageLoadState.Progress(progress, postImageData))
          }
        )

        progressResponseBody.use { prb ->
          prb.source().use { source ->
            diskFile.outputStream().sink().use { sink ->
              source.readAll(sink)
            }
          }
        }
      }

      kurobaLruDiskCache.markFileDownloaded(cacheFileType, diskFile)
      return diskFile
    } catch (error: Throwable) {
      kurobaLruDiskCache.deleteCacheFile(cacheFileType, diskFile)
      throw error
    } finally {
      responseBody.closeQuietly()
    }
  }

  fun installMpvLibsFromGithub(
    mpvSettings: MpvSettings,
    showLoading: () -> Unit,
    hideLoading: () -> Unit,
    onFailed: (Throwable) -> Unit,
    onSuccess: () -> Unit
  ) {
    viewModelScope.launch {
      val result = try {
        showLoading()
        installMpvNativeLibrariesFromGithub.execute(mpvSettings)
      } finally {
        hideLoading()
      }

      if (result.isFailure) {
        val error = result.exceptionOrThrow()
        logcatError(TAG) { "installMpvLibrariesFromGithub error: ${error.asLog()}" }

        onFailed(error)
      } else {
        logcat(TAG) { "installMpvLibrariesFromGithub success" }

        onSuccess()
      }
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

    const val MAX_RETRIES = 5
  }

}