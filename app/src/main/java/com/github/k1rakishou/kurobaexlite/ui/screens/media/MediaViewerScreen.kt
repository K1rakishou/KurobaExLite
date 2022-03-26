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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImage
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImageDecoder
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImageEventListener
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImageSource
import com.github.k1rakishou.cssi_lib.ImageDecoderProvider
import com.github.k1rakishou.cssi_lib.ImageSourceProvider
import com.github.k1rakishou.cssi_lib.MaxTileSize
import com.github.k1rakishou.cssi_lib.MinimumScaleType
import com.github.k1rakishou.cssi_lib.rememberComposeSubsamplingScaleImageState
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.decoder.TachiyomiImageDecoder
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.LeftIconInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.MiddlePartInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import java.util.Locale
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
    val insets = LocalWindowInsets.current
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
    ) {
      var currentImageIndex by remember { mutableStateOf(0) }
      var targetImageIndex by remember { mutableStateOf(0) }
      var offset by remember { mutableStateOf(0f) }

      MediaViewerPager(
        mediaViewerScreenState = mediaViewerScreenState,
        onPageChanged = { currentPage, targetPage, pageOffset ->
          currentImageIndex = currentPage
          targetImageIndex = targetPage
          offset = pageOffset
        }
      )

      MediaViewerToolbar(
        mediaViewerScreenState = mediaViewerScreenState,
        currentImageIndex = currentImageIndex,
        targetImageIndex = targetImageIndex,
        offset = offset
      )
    }
  }

  @Composable
  private fun MediaViewerToolbar(
    mediaViewerScreenState: MediaViewerScreenState,
    currentImageIndex: Int,
    targetImageIndex: Int,
    offset: Float
  ) {
    if (mediaViewerScreenState.isLoaded()) {
      val childToolbars = remember(key1 = currentImageIndex, key2 = targetImageIndex) {
        val childToolbars = mutableListOf<ChildToolbar>()

        childToolbars += ChildToolbar(
          key = mediaViewerScreenState.requireImages().get(currentImageIndex).fullImageUrlAsString,
          indexInList = currentImageIndex,
          content = {
            MediaToolbar(
              mediaViewerScreenState = mediaViewerScreenState,
              currentPagerPage = currentImageIndex
            )
          })

        if (currentImageIndex != targetImageIndex) {
          childToolbars += ChildToolbar(
            key = mediaViewerScreenState.requireImages().get(targetImageIndex).fullImageUrlAsString,
            indexInList = targetImageIndex,
            content = {
              MediaToolbar(
                mediaViewerScreenState = mediaViewerScreenState,
                currentPagerPage = targetImageIndex
              )
            })
        }

        return@remember childToolbars
      }

      MediaViewerScreenToolbarContainer(
        currentToolbarIndex = currentImageIndex,
        targetToolbarIndex = targetImageIndex,
        offset = offset,
        childToolbars = childToolbars
      )
    }
  }

  @Composable
  private fun MediaToolbar(
    mediaViewerScreenState: MediaViewerScreenState,
    currentPagerPage: Int
  ) {
    val coroutineScope = rememberCoroutineScope()

    val kurobaToolbarState = remember {
      return@remember KurobaToolbarState(
        leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_arrow_back_24),
        middlePartInfo = MiddlePartInfo(centerContent = false),
      )
    }

    UpdateMediaViewerToolbarTitle(
      mediaViewerScreenState = mediaViewerScreenState,
      kurobaToolbarState = kurobaToolbarState,
      currentPagerPage = currentPagerPage
    )

    KurobaToolbar(
      screenKey = screenKey,
      componentActivity = componentActivity,
      kurobaToolbarState = kurobaToolbarState,
      navigationRouter = navigationRouter,
      canProcessBackEvent = { true },
      onLeftIconClicked = { coroutineScope.launch { onBackPressed() } },
      onMiddleMenuClicked = {
        // no-op
      },
      onSearchQueryUpdated = null,
      onToolbarSortClicked = null,
      onToolbarOverflowMenuClicked = {
        // no-op
      }
    )
  }

  @Composable
  private fun UpdateMediaViewerToolbarTitle(
    mediaViewerScreenState: MediaViewerScreenState,
    kurobaToolbarState: KurobaToolbarState,
    currentPagerPage: Int
  ) {
    val isLoaded = mediaViewerScreenState.isLoaded()
    if (!isLoaded) {
      return
    }

    val currentImageData = remember(key1 = currentPagerPage) {
      val images = mediaViewerScreenState.images
        ?: return@remember null

      return@remember images.getOrNull(currentPagerPage)?.postImageData
    }

    LaunchedEffect(
      key1 = currentImageData,
      block = {
        if (currentImageData == null) {
          return@LaunchedEffect
        }

        Snapshot.withMutableSnapshot {
          val imagesCount = mediaViewerScreenState.images?.size

          kurobaToolbarState.toolbarTitleState.value = currentImageData.originalFileName
          kurobaToolbarState.toolbarSubtitleState.value = formatImageInfo(
            currentPagerPage = currentPagerPage,
            imagesCount = imagesCount,
            currentImageData = currentImageData
          )
        }
      })
  }

  private fun formatImageInfo(
    currentPagerPage: Int,
    imagesCount: Int?,
    currentImageData: PostImageData
  ): String {
    return buildString {
      append(currentPagerPage + 1)
      append("/")
      append(imagesCount?.toString() ?: "?")
      append(", ")

      append(currentImageData.ext.uppercase(Locale.ENGLISH))
      append(", ")

      append(currentImageData.width)
      append("x")
      append(currentImageData.height)
      append(", ")

      append(currentImageData.fileSize.asReadableFileSize())
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun MediaViewerPager(
    mediaViewerScreenState: MediaViewerScreenState,
    onPageChanged: (Int, Int, Float) -> Unit
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

    LaunchedEffect(
      key1 = pagerState.currentPage,
      key2 = pagerState.targetPage,
      key3 = pagerState.currentPageOffset,
      block = { onPageChanged(pagerState.currentPage, pagerState.targetPage, pagerState.currentPageOffset) }
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
            currentPage = { pagerState.currentPage },
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
    currentPage: () -> Int,
    postImageDataLoadState: ImageLoadState.Ready,
    onFullImageLoaded: () -> Unit,
    onFullImageFailedToLoad: () -> Unit
  ) {
    val eventListener = object : ComposeSubsamplingScaleImageEventListener() {
      override fun onFailedToLoadFullImage(error: Throwable) {
        val url = postImageDataLoadState.fullImageUrlAsString
        logcat { "onFailedToLoadFullImage() url=$url, error=${error.errorMessageOrClassName()}" }

        onFullImageFailedToLoad()
      }

      override fun onFullImageLoaded() {
        onFullImageLoaded()
      }
    }

    val imageSourceProvider = remember(key1 = postImageDataLoadState.imageFile) {
      object : ImageSourceProvider {
        override suspend fun provide(): ComposeSubsamplingScaleImageSource {
          return ComposeSubsamplingScaleImageSource(
            debugKey = postImageDataLoadState.fullImageUrlAsString,
            inputStream = postImageDataLoadState.imageFile.inputStream()
          )
        }
      }
    }

    val imageDecoderProvider = remember {
      object : ImageDecoderProvider {
        override suspend fun provide(): ComposeSubsamplingScaleImageDecoder {
          return TachiyomiImageDecoder()
        }
      }
    }

    // TODO(KurobaEx): add onClick once it's supported
    //  onClick = { coroutineScope.launch { onBackPressed() } }

    ComposeSubsamplingScaleImage(
      modifier = Modifier.fillMaxSize(),
      pointerInputKey = currentPage(),
      state = rememberComposeSubsamplingScaleImageState(
        doubleTapZoom = 2f,
        maxScale = 3f,
        maxMaxTileSize = { MaxTileSize.Auto() },
        minimumScaleType = { MinimumScaleType.ScaleTypeCenterInside },
        imageDecoderProvider = imageDecoderProvider
      ),
      imageSourceProvider = imageSourceProvider,
      eventListener = eventListener
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