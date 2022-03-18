package com.github.k1rakishou.kurobaexlite.ui.screens.media

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image.ComposeSubsamplingImage
import com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image.ComposeSubsamplingImageSource
import com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image.MaxTileSizeInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image.rememberComposeSubsamplingImageState
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.launch
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel

class MediaViewerScreen(
  private val mediaViewerParams: MediaViewerParams,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val mediaViewerScreenViewModel: MediaViewerScreenViewModel by componentActivity.viewModel()
  private val bgColor = Color.Black.copy(alpha = 0.5f)

  override val screenKey: ScreenKey = SCREEN_KEY

  class MediaViewerScreenState {
    var images: SnapshotStateList<ImageLoadState>? = null
    val initialPage = mutableStateOf<Int?>(null)

    fun isLoaded(): Boolean = initialPage.value != null && images != null
    fun requireImages(): SnapshotStateList<ImageLoadState> = requireNotNull(images) { "images not initialized yet!" }
  }

  @Composable
  override fun CardContent() {
    val coroutineScope = rememberCoroutineScope()
    val mediaViewerScreenState = remember { MediaViewerScreenState() }

    LaunchedEffect(
      key1 = mediaViewerParams,
      block = {
        val initResult = mediaViewerScreenViewModel.init(mediaViewerParams)

        Snapshot.withMutableSnapshot {
          val images = mutableStateListOf<ImageLoadState>()
          images.addAll(initResult.images)

          mediaViewerScreenState.images = images
          mediaViewerScreenState.initialPage.value = initResult.initialPage
        }
      }
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(bgColor)
        .kurobaClickable(
          hasClickIndication = false,
          onClick = { coroutineScope.launch { onBackPressed() } }
        )
    ) {
      ActualContent(mediaViewerScreenState)
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun ActualContent(
    mediaViewerScreenState: MediaViewerScreenState
  ) {
    if (!mediaViewerScreenState.isLoaded()) {
      return
    }

    val configuration = LocalConfiguration.current
    val initialPageMut by mediaViewerScreenState.initialPage
    val imagesMut = mediaViewerScreenState.images

    val initialPage = initialPageMut
    val images = imagesMut

    if (initialPage == null || images == null) {
      return
    }

    if (images.isEmpty()) {
      InsetsAwareBox(modifier = Modifier.fillMaxSize()) {
        KurobaComposeText(text = "No images to show")
      }

      return
    }

    SideEffect {
      logcat { "initialPage=$initialPage, initialImage=${images[initialPage].fullImageUrlAsString}" }
    }

    val pagerState = rememberPagerState(
      key1 = configuration.orientation,
      initialPage = initialPage
    )

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      count = images.size,
      state = pagerState,
      key = { page -> images[page].fullImageUrlAsString }
    ) { page ->
      val postImageDataLoadState = images[page]

      val displayImagePreviewMovable = remember {
        movableContentOf {
          DisplayImagePreview(postImageDataLoadState)
        }
      }

      when (postImageDataLoadState) {
        is ImageLoadState.Loading -> {
          LoadFullImage(mediaViewerScreenState, postImageDataLoadState)
          displayImagePreviewMovable()
        }
        is ImageLoadState.Ready -> {
          var fullImageLoaded by remember { mutableStateOf(false) }
          if (!fullImageLoaded) {
            displayImagePreviewMovable()
          }

          DisplayFullImage(
            postImageDataLoadState = postImageDataLoadState,
            onFullImageLoaded = { fullImageLoaded = true },
            onFullImageFailedToLoad = { fullImageLoaded = false }
          )
        }
        is ImageLoadState.Error -> {
          DisplayImageLoadError(postImageDataLoadState)
        }
      }
    }
  }

  @Composable
  private fun LoadFullImage(
    mediaViewerScreenState: MediaViewerScreenState,
    postImageDataLoadState: ImageLoadState.Loading
  ) {
    LaunchedEffect(
      key1 = postImageDataLoadState.postImageData,
      block = {
        val postImageData = postImageDataLoadState.postImageData
        val fullImageUrl = postImageData.fullImageUrl

        val index = mediaViewerScreenState.requireImages().indexOfFirst { imageLoadState ->
          imageLoadState.fullImageUrl == postImageData.fullImageUrl
        }

        val imageLoadState = if (index >= 0) {
          mediaViewerScreenViewModel.loadFullImageAndGetFile(postImageDataLoadState.postImageData)
        } else {
          // TODO(KurobaEx): strings
          val exception =  MediaViewerScreenViewModel.ImageLoadException(
            fullImageUrl,
            "Failed to find previous image in images"
          )

          ImageLoadState.Error(postImageData, exception)
        }

        mediaViewerScreenState.requireImages().set(index, imageLoadState)
      }
    )
  }

  @Composable
  private fun DisplayImageLoadError(postImageDataLoadState: ImageLoadState.Error) {
    InsetsAwareBox(modifier = Modifier.fillMaxSize()) {
      // TODO(KurobaEx):
      KurobaComposeText(text = "Error: ${postImageDataLoadState.exception.errorMessageOrClassName()}")
    }
  }

  @Composable
  private fun DisplayFullImage(
    postImageDataLoadState: ImageLoadState.Ready,
    onFullImageLoaded: () -> Unit,
    onFullImageFailedToLoad: () -> Unit
  ) {
    ComposeSubsamplingImage(
      modifier = Modifier.fillMaxSize(),
      state = rememberComposeSubsamplingImageState(
        maxMaxTileSizeInfo = MaxTileSizeInfo.Fixed(IntSize(192, 192)),
        sourceDebugKey = postImageDataLoadState.fullImageUrlAsString,
        debug = true
      ),
      imageSource = { ComposeSubsamplingImageSource.Stream(postImageDataLoadState.imageFile.inputStream()) },
      onFullImageLoaded = onFullImageLoaded,
      onFullImageFailedToLoad = onFullImageFailedToLoad
    )
  }

  @Composable
  private fun DisplayImagePreview(postImageDataState: ImageLoadState) {
    val postImageDataLoadState = (postImageDataState as? ImageLoadState.Loading)
      ?: return

    val context = LocalContext.current
    val postImageData = postImageDataLoadState.postImageData

    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(context)
        .data(postImageData.thumbnailUrl)
        .crossfade(false)
        .build(),
      contentDescription = null,
      contentScale = ContentScale.Fit,
      content = {
        val state = painter.state
        if (state is AsyncImagePainter.State.Error) {
          logcatError(tag = TAG) {
            "DisplayImagePreview() url=${postImageData}, " +
              "postDescriptor=${postImageData.ownerPostDescriptor}, " +
              "error=${state.result.throwable}"
          }
        }

        SubcomposeAsyncImageContent()
      }
    )
  }



  companion object {
    private const val TAG = "MediaViewerScreen"
    val SCREEN_KEY = ScreenKey("MediaViewerScreen")
  }

}