package com.github.k1rakishou.kurobaexlite.features.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.State
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.k1rakishou.chan.core.mpv.MpvInitializer
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.CacheFileType
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.KurobaLruDiskCache
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.network.ProgressResponseBody
import com.github.k1rakishou.kurobaexlite.helpers.suspendCall
import com.github.k1rakishou.kurobaexlite.interactors.InstallMpvNativeLibrariesFromGithub
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
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


class MediaViewerScreenViewModel(
  val mpvSettings: MpvSettings,
  private val mpvInitializer: MpvInitializer,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val kurobaLruDiskCache: KurobaLruDiskCache,
  private val installMpvNativeLibrariesFromGithub: InstallMpvNativeLibrariesFromGithub,
  private val imageLoader: ImageLoader,
  private val mediaSaver: MediaSaver,
) : BaseViewModel() {

  val mpvInitialized: Boolean
    get() = mpvInitializer.initialized

  suspend fun initFromPostStateList(
    postCellDataStateList: List<State<PostCellData>>,
    initialImageUrl: HttpUrl
  ): InitResult {
    return withContext(Dispatchers.Default) {
      // Trim the cache every time we open the media viewer
      kurobaLruDiskCache.manualTrim(CacheFileType.PostMediaFull)

      val imagesToShow = mutableListOf<IPostImage>()

      postCellDataStateList.forEach { postCellDataState ->
        val postCellData = postCellDataState.value

        val threadImages = postCellData.images
          ?: return@forEach

        if (threadImages.isNotEmpty()) {
          imagesToShow += threadImages
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

  suspend fun initFromImageList(
    images: List<PostCellImageData>,
    initialImageUrl: HttpUrl
  ): InitResult {
    return withContext(Dispatchers.Default) {
      // Trim the cache every time we open the media viewer
      kurobaLruDiskCache.manualTrim(CacheFileType.PostMediaFull)

      var initialPage = images.indexOfFirst { it.fullImageAsUrl == initialImageUrl }
      if (initialPage < 0) {
        logcatError { "Failed to find post image with url: \'${initialImageUrl}\', resetting it" }
        initialPage = 0
      }

      val hasVideos = images.any { postImage -> postImage.imageType() == ImageType.Video }
      if (hasVideos) {
        mpvInitializer.init()
      }

      return@withContext InitResult(
        images = images.map { postImageData -> ImageLoadState.PreparingForLoading(postImageData) },
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

  suspend fun removeFileFromDisk(postImage: IPostImage) {
    kurobaLruDiskCache.deleteCacheFileByUrl(
      cacheFileType = CacheFileType.PostMediaFull,
      url = postImage.fullImageAsUrl
    )
  }

  suspend fun loadThumbnailBitmap(context: Context, postImage: IPostImage): Bitmap? {
    val imageRequest = ImageRequest.Builder(context)
      .data(postImage.thumbnailAsUrl)
      .build()

    // Ideally this will only load the bitmap from the memory/disk cache.
    val imageResult = imageLoader.execute(imageRequest)

    if (imageResult is ErrorResult) {
      logcatError { "Failed to load thumbnail bitmap, error: ${imageResult.throwable.errorMessageOrClassName()}" }
      return null
    }

    imageResult as SuccessResult

    return (imageResult.drawable as BitmapDrawable).bitmap
  }

  fun downloadMedia(
    postImage: IPostImage,
    onResult: (Result<Unit>) -> Unit
  ) {
    viewModelScope.launch {
      val result = mediaSaver.savePostImage(postImage)
      onResult(result)
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