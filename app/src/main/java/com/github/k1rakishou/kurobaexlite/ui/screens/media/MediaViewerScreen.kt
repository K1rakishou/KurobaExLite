package com.github.k1rakishou.kurobaexlite.ui.screens.media

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.disk.DiskCache
import coil.request.ImageRequest
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.suspendCall
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.asLog
import okhttp3.HttpUrl
import okhttp3.Request
import okio.Path
import org.koin.java.KoinJavaComponent.inject

class MediaViewerScreen(
  private val chanDescriptor: ChanDescriptor,
  private val inputImages: List<PostImageData>,
  private val initialImageUrl: HttpUrl,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val proxiedOkHttpClient: ProxiedOkHttpClient by inject(ProxiedOkHttpClient::class.java)
  private val diskCache: DiskCache by inject(DiskCache::class.java)

  private val bgColor = Color.Black.copy(alpha = 0.5f)
  private val images = mutableStateListOf<ImageLoadState>()
  private val initialPage: Int

  override val screenKey: ScreenKey = SCREEN_KEY

  init {
    inputImages.forEach { postImageData ->
      images.add(ImageLoadState.Loading(postImageData))
    }

    initialPage = inputImages.indexOfFirst { it.fullImageUrl == initialImageUrl }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    navigationRouter.HandleBackPresses(
      screenKey = screenKey,
      onBackPressed = { popScreen() }
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(bgColor)
    ) {
      val configuration = LocalConfiguration.current

      val pagerState = rememberPagerState(
        key1 = configuration.orientation,
        initialPage = initialPage
      )

      HorizontalPager(
        count = images.size,
        state = pagerState
      ) { page ->
        when (val postImageDataLoadState = images[page]) {
          is ImageLoadState.Loading -> {
            LoadFullImage(postImageDataLoadState)
            DisplayImagePreview(postImageDataLoadState)
          }
          is ImageLoadState.Error -> {
            DisplayImageLoadError(postImageDataLoadState)
          }
          is ImageLoadState.Ready -> {
            DisplayFullImage(postImageDataLoadState)
          }
        }
      }
    }
  }

  @Composable
  private fun LoadFullImage(postImageDataLoadState: ImageLoadState.Loading) {
    LaunchedEffect(
      key1 = postImageDataLoadState.postImageData.fullImageUrl,
      block = {
        val postImageData = postImageDataLoadState.postImageData

        val loadImageResult = withContext(Dispatchers.IO) {
          Result.Try { loadFullImage(postImageDataLoadState.postImageData) }
        }

        val index = images.indexOfFirst { imageLoadState ->
          imageLoadState.fullImageUrl == postImageData.fullImageUrl
        }

        if (index < 0) {
          return@LaunchedEffect
        }

        if (loadImageResult.isSuccess) {
          val resultFilePath = loadImageResult.getOrThrow()
          images.set(index, ImageLoadState.Ready(postImageData, resultFilePath.toFile()))
        } else {
          val error = loadImageResult.exceptionOrNull()!!
          images.set(index, ImageLoadState.Error(postImageData, error))
        }
      }
    )
  }

  @Composable
  private fun DisplayImageLoadError(postImageDataLoadState: ImageLoadState.Error) {
    // TODO(KurobaEx):
    KurobaComposeText(text = "Error: ${postImageDataLoadState.exception.asLog()}")
  }

  @Composable
  private fun DisplayFullImage(postImageDataLoadState: ImageLoadState.Ready) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        SubsamplingScaleImageView(context).also { imageView ->
          imageView.setImage(ImageSource.inputStream(postImageDataLoadState.imageFile.inputStream()))
        }
      }
    )
  }

  @Composable
  private fun DisplayImagePreview(postImageDataLoadState: ImageLoadState.Loading) {
    val context = LocalContext.current
    val postImageData = postImageDataLoadState.postImageData

    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(context)
        .data(postImageData.thumbnailUrl)
        .crossfade(true)
        .build(),
      contentDescription = null,
      contentScale = ContentScale.Fit,
      content = {
        val state = painter.state
        if (state is AsyncImagePainter.State.Error) {
          logcatError {
            "DisplayImagePreview() url=${postImageData}, " +
              "postDescriptor=${postImageData.ownerPostDescriptor}, " +
              "error=${state.result.throwable}"
          }
        }

        SubcomposeAsyncImageContent()
      }
    )
  }

  private suspend fun loadFullImage(postImageData: PostImageData): Path {
    BackgroundUtils.ensureBackgroundThread()

    val imageUrl = postImageData.fullImageUrl

    val request = Request.Builder()
      .url(imageUrl)
      .get()
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val responseBody = response.body
      ?: throw EmptyBodyResponseException()

    val diskCacheKey = imageUrl.toString()
    val snapshot = diskCache.get(diskCacheKey)

    val editor = if (snapshot != null) {
      snapshot.closeAndEdit()
    } else {
      diskCache.edit(diskCacheKey)
    }

    if (editor == null) {
      throw ImageLoadException(imageUrl, "Failed to edit disk cache")
    }

    try {
      diskCache.fileSystem.write(editor.data) {
        responseBody.source().readAll(this)
      }

      return editor.commitAndGet()?.data
        ?: throw ImageLoadException(imageUrl, "Failed to store in disk cache")
    } catch (e: Exception) {
      try {
        editor.abort()
      } catch (ignored: Exception) {
      }

      throw e
    }
  }

  class ImageLoadException : ClientException {
    constructor(url: HttpUrl) : super("Failed to load image \'$url\'")
    constructor(url: HttpUrl, cause: Throwable) : super("Failed to load image \'$url\'", cause)
    constructor(url: HttpUrl, message: String) : super("Failed to load image \'$url\', reason: $message")
  }

  sealed class ImageLoadState {
    val fullImageUrl: HttpUrl
      get() {
        return when (this) {
          is Loading -> postImageData.fullImageUrl
          is Error -> postImageData.fullImageUrl
          is Ready -> postImageData.fullImageUrl
        }
      }

    data class Loading(val postImageData: PostImageData) : ImageLoadState()

    data class Error(
      val postImageData: PostImageData,
      val exception: Throwable
    ) : ImageLoadState()

    data class Ready(
      val postImageData: PostImageData,
      val imageFile: File
    ) : ImageLoadState()
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MediaViewerScreen")
  }

}