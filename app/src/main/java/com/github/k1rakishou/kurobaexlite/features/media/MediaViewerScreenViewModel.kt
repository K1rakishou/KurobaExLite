package com.github.k1rakishou.kurobaexlite.features.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.GuardedBy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.k1rakishou.chan.core.mpv.MpvInitializer
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.CacheFileType
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.KurobaLruDiskCache
import com.github.k1rakishou.kurobaexlite.helpers.network.ProgressResponseBody
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.resource.IAppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.interactors.InstallMpvNativeLibrariesFromGithub
import com.github.k1rakishou.kurobaexlite.managers.RevealedSpoilerImages
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.internal.closeQuietly
import okio.sink
import java.io.File


class MediaViewerScreenViewModel(
  private val savedStateHandle: SavedStateHandle,
  val mpvSettings: MpvSettings,
  private val mpvInitializer: MpvInitializer,
  private val appSettings: AppSettings,
  private val appResources: IAppResources,
  private val chanPostCache: IChanPostCache,
  private val postHideRepository: IPostHideRepository,
  private val siteManager: SiteManager,
  private val proxiedOkHttpClient: IKurobaOkHttpClient,
  private val kurobaLruDiskCache: KurobaLruDiskCache,
  private val installMpvNativeLibrariesFromGithub: InstallMpvNativeLibrariesFromGithub,
  private val imageLoader: ImageLoader,
  private val mediaSaver: MediaSaver,
  private val postReplyChainRepository: IPostReplyChainRepository,
  private val revealedSpoilerImages: RevealedSpoilerImages,
) : BaseViewModel() {

  val mpvInitialized: Boolean
    get() = mpvInitializer.initialized

  val mediaViewerScreenState = MediaViewerScreenState(
    savedStateHandle = savedStateHandle,
    appSettings = appSettings,
    appResources = appResources,
    chanPostCache = chanPostCache
  )

  @GuardedBy("itself")
  private val activeMediaLoadRequests = mutableSetOf<String>()

  suspend fun initFromCatalogOrThreadDescriptor(
    chanDescriptor: ChanDescriptor,
    initialImageUrl: HttpUrl
  ): InitResult {
    return withContext(Dispatchers.Default) {
      logcat(TAG) {
        "initFromPostStateList() " +
          "chanDescriptor=${chanDescriptor}, " +
          "initialImageUrl=${initialImageUrl} start"
      }

      // Trim the cache every time we open the media viewer
      kurobaLruDiskCache.manualTrim(CacheFileType.PostMediaFull)

      val imagesToShow = mutableListOf<IPostImage>()

      val posts = when (chanDescriptor) {
        is CatalogDescriptor -> chanPostCache.getCatalogThreads(chanDescriptor)
        is ThreadDescriptor -> chanPostCache.getThreadPosts(chanDescriptor)
      }

      posts.forEach { post ->
        if (postHideRepository.isPostHidden(post.postDescriptor)) {
          return@forEach
        }

        val imagesOfThisPost = post.images
          ?: return@forEach

        imagesOfThisPost.forEach { postImage ->
          imagesToShow += postImage
        }
      }

      var initialPage = imagesToShow.indexOfFirst { it.fullImageAsUrl == initialImageUrl }
      if (initialPage < 0) {
        logcatError(TAG) { "Failed to find post image with url: \'${initialImageUrl}\', resetting it" }
        initialPage = 0
      }

      val hasVideos = imagesToShow.any { postImage -> postImage.imageType() == ImageType.Video }
      if (hasVideos) {
        mpvInitializer.init()
      }

      val images = imagesToShow.map { postImageData -> ImageLoadState.PreparingForLoading(postImageData) }

      logcat(TAG) {
        "initFromPostStateList() " +
          "chanDescriptor=${chanDescriptor}, " +
          "images=${images.size}, " +
          "initialImageUrl=${initialImageUrl}, " +
          "initialPage=${initialPage} end"
      }

      return@withContext InitResult(
        images = images,
        initialPage = initialPage,
        sourceType = SourceType.CatalogOrThread
      )
    }
  }

  suspend fun initFromImageList(
    chanDescriptor: ChanDescriptor,
    images: List<String>,
    initialImageUrl: HttpUrl
  ): InitResult {
    return withContext(Dispatchers.Default) {
      logcat(TAG) {
        "initFromImageList() " +
          "chanDescriptor=${chanDescriptor}, " +
          "inputImages=${images.size}, " +
          "initialImageUrl=${initialImageUrl} start"
      }

      // Trim the cache every time we open the media viewer
      kurobaLruDiskCache.manualTrim(CacheFileType.PostMediaFull)

      val posts = when (chanDescriptor) {
        is CatalogDescriptor -> chanPostCache.getCatalogThreads(chanDescriptor)
        is ThreadDescriptor -> chanPostCache.getThreadPosts(chanDescriptor)
      }

      val postImages = mutableListWithCap<IPostImage>(posts.size)
      val imagesAsSet = images.toSet()

      posts.forEach { post ->
        val imagesOfThisPost = post.images
          ?: return@forEach

        imagesOfThisPost.forEach { postImage ->
          if (postImage.fullImageAsString in imagesAsSet) {
            postImages += postImage
          }
        }
      }

      var initialPage = postImages.indexOfFirst { it.fullImageAsUrl == initialImageUrl }
      if (initialPage < 0) {
        logcatError(TAG) { "Failed to find post image with url: \'${initialImageUrl}\', resetting it" }
        initialPage = 0
      }

      val hasVideos = postImages.any { postImage -> postImage.imageType() == ImageType.Video }
      if (hasVideos) {
        mpvInitializer.init()
      }

      logcat(TAG) {
        "initFromImageList() " +
          "chanDescriptor=${chanDescriptor}, " +
          "postImages=${postImages.size}, " +
          "initialImageUrl=${initialImageUrl}, " +
          "initialPage=${initialPage} end"
      }

      return@withContext InitResult(
        images = postImages.map { postImageData -> ImageLoadState.PreparingForLoading(postImageData) },
        initialPage = initialPage,
        sourceType = SourceType.ImageLinkList
      )
    }
  }

  fun onScreenDisposed() {
    mediaViewerScreenState.destroy()
    mpvInitializer.destroy()
  }

  fun enqueueMediaLoadRequest(fullImageUrlAsString: String): Boolean {
    return synchronized(activeMediaLoadRequests) { activeMediaLoadRequests.add(fullImageUrlAsString) }
  }

  fun removeEnqueuedMediaLoadRequest(fullImageUrlAsString: String): Boolean {
    return synchronized(activeMediaLoadRequests) { activeMediaLoadRequests.remove(fullImageUrlAsString) }
  }

  suspend fun loadFullImageAndGetFile(
    postImageData: IPostImage
  ): Flow<ImageLoadState> {
    return channelFlow {
      val fullImageUrl = postImageData.fullImageAsUrl

      val loadImageResult = withContext(Dispatchers.IO) {
        try {
          logcat(TAG, LogPriority.VERBOSE) { "loadFullImageInternal(${fullImageUrl}) start" }
          Result.Try { loadFullImageInternal(postImageData, this@channelFlow) }
        } finally {
          logcat(TAG, LogPriority.VERBOSE) { "loadFullImageInternal(${fullImageUrl}) end" }
        }
      }

      if (!loadImageResult.isSuccess) {
        val error = loadImageResult.exceptionOrNull()!!
        logcatError(TAG) { "loadFullImageAndGetFile() Failed to load \'$fullImageUrl\', error=${error.asLog()}" }

        send(ImageLoadState.Error(postImageData, error))
        return@channelFlow
      }

      val resultFile = loadImageResult.getOrThrow()
      logcat(TAG, LogPriority.VERBOSE) { "loadFullImageAndGetFile() Successfully loaded \'$fullImageUrl\'" }
      send(ImageLoadState.Ready(postImageData, resultFile, resultFile.absolutePath))

      revealedSpoilerImages.onFullImageOpened(postImageData)
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
      logcat(TAG, LogPriority.VERBOSE) { "loadFullImage($fullImageUrl) got image from disk cache (${cachedFile.absolutePath})" }
      return cachedFile
    }

    val request = Request.Builder()
      .url(fullImageUrl)
      .get()
      .also { requestBuilder ->
        siteManager.bySiteKey(postImageData.ownerPostDescriptor.siteKey)
          ?.requestModifier()
          ?.modifyGetMediaRequest(requestBuilder)
      }
      .build()

    val response = proxiedOkHttpClient.okHttpClient()
      .suspendCall(request)
      .unwrap()

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
  ): Job {
    return viewModelScope.launch {
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
    val imageResult = try {
      imageLoader.execute(imageRequest)
    } catch (error: Throwable) {
      return null
    }

    if (imageResult is ErrorResult) {
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

  suspend fun getReplyCountToPost(postDescriptor: PostDescriptor): Int {
    return withContext(viewModelScope.coroutineContext) {
      return@withContext postReplyChainRepository.getRepliesFrom(postDescriptor).size
    }
  }

  class InitResult(
    val images: List<ImageLoadState>,
    val initialPage: Int,
    val sourceType: SourceType
  )

  enum class SourceType {
    CatalogOrThread,
    ImageLinkList
  }

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