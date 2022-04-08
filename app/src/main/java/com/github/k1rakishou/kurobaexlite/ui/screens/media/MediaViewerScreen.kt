package com.github.k1rakishou.kurobaexlite.ui.screens.media

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.progress.ProgressScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers.DisplayLoadingProgressIndicator
import com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers.MediaViewerScreenVideoControls
import com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers.MediaViewerToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.DisplayFullImage
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.DisplayUnsupportedMedia
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.DisplayVideo
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.VideoMediaState
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject


class MediaViewerScreen(
  private val mediaViewerParams: MediaViewerParams,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val mediaViewerScreenViewModel: MediaViewerScreenViewModel by componentActivity.viewModel()
  private val mediaViewerPostListScroller: MediaViewerPostListScroller by inject(MediaViewerPostListScroller::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY

  class MediaViewerScreenState {
    var images: SnapshotStateList<ImageLoadState>? = null
    val initialPage = mutableStateOf<Int?>(null)

    fun isLoaded(): Boolean = initialPage.value != null && images != null
    fun requireImages(): SnapshotStateList<ImageLoadState> = requireNotNull(images) { "images not initialized yet!" }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun CardContent() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val coroutineScope = rememberCoroutineScope()
    val mediaViewerScreenState = remember { MediaViewerScreenState() }
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

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

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose { mediaViewerScreenViewModel.destroy() }
      }
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
      var pagerStateHolder by remember { mutableStateOf<PagerState?>(null) }

      val videoMediaState = remember(key1 = pagerStateHolder?.currentPage) {
        val currentPage = pagerStateHolder?.currentPage
          ?: return@remember null

        val videoControlsVisible = mediaViewerScreenState.images
          ?.getOrNull(currentPage)
          ?.postImageData
          ?.imageType() == ImageType.Video

        return@remember VideoMediaState(currentPage, videoControlsVisible)
      }

      val bgColor = remember(chanTheme.primaryColorCompose) {
        chanTheme.primaryColorCompose.copy(alpha = 0.5f)
      }

      MediaViewerPager(
        toolbarHeight = toolbarHeight,
        mediaViewerScreenState = mediaViewerScreenState,
        onViewPagerInitialized = { pagerState -> pagerStateHolder = pagerState },
        videoMediaState = videoMediaState
      )

      if (pagerStateHolder != null) {
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .background(bgColor)
        ) {
          MediaViewerToolbar(
            toolbarHeight = toolbarHeight,
            screenKey = screenKey,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            mediaViewerScreenState = mediaViewerScreenState,
            pagerState = pagerStateHolder,
            onBackPressed = { coroutineScope.launch { onBackPressed() } }
          )
        }
      }

      if (videoMediaState != null && mediaViewerScreenViewModel.mpvInitialized) {
        val videoControlsVisible by videoMediaState.videoControlsVisibleState
        val targetAlpha = if (videoControlsVisible) 1f else 0f
        val alphaAnimated by animateFloatAsState(
          targetValue = targetAlpha,
          animationSpec = tween(durationMillis = 250)
        )

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .graphicsLayer { this.alpha = alphaAnimated }
            .background(bgColor)
        ) {
          MediaViewerScreenVideoControls(videoMediaState = videoMediaState)

          Spacer(modifier = Modifier.height(insets.bottom))
        }
      }
    }
  }


  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun MediaViewerPager(
    toolbarHeight: Dp,
    mediaViewerScreenState: MediaViewerScreenState,
    onViewPagerInitialized: (PagerState) -> Unit,
    videoMediaState: VideoMediaState?
  ) {
    if (!mediaViewerScreenState.isLoaded()) {
      return
    }

    val orientationMut by uiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val context = LocalContext.current
    val initialPageMut by mediaViewerScreenState.initialPage
    val imagesMut = mediaViewerScreenState.images

    val initialPage = initialPageMut
    val images = imagesMut

    if (initialPage == null || images == null) {
      return
    }

    if (images.isEmpty()) {
      val additionalPaddings = remember(toolbarHeight) { PaddingValues(top = toolbarHeight) }

      InsetsAwareBox(
        modifier = Modifier.fillMaxSize(),
        additionalPaddings = additionalPaddings
      ) {
        KurobaComposeText(text = "No images to show")
      }

      return
    }

    val mpvSettings = mediaViewerScreenViewModel.mpvSettings

    val mpvLibsInstalledAndLoaded = remember {
      lazy {
        val librariesInstalled = MPVLib.checkLibrariesInstalled(context.applicationContext, mpvSettings)
        if (!librariesInstalled) {
          return@lazy false
        }

        return@lazy MPVLib.librariesAreLoaded()
      }
    }

    var initialPageLoaded by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
      key1 = orientation,
      initialPage = initialPage
    )

    LaunchedEffect(
      key1 = Unit,
      block = { onViewPagerInitialized(pagerState) }
    )

    LaunchedEffect(
      key1 = pagerState.currentPage,
      block = {
        if (initialPage == pagerState.currentPage && !initialPageLoaded) {
          initialPageLoaded = true
          return@LaunchedEffect
        }

        val postImageData = images.getOrNull(pagerState.currentPage)?.postImageData
          ?: return@LaunchedEffect

        mediaViewerPostListScroller.onSwipedTo(
          fullImageUrl = postImageData.fullImageAsUrl,
          postDescriptor = postImageData.ownerPostDescriptor
        )
      }
    )

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      count = images.size,
      state = pagerState,
      key = { page -> images[page].fullImageUrlAsString }
    ) { page ->
      PagerContent(
        context = context,
        page = page,
        images = images,
        mediaViewerScreenState = mediaViewerScreenState,
        pagerState = pagerState,
        toolbarHeight = toolbarHeight,
        mpvSettings = mpvSettings,
        mpvLibsInstalledAndLoaded = mpvLibsInstalledAndLoaded,
        videoMediaState = videoMediaState
      )
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun PagerContent(
    context: Context,
    page: Int,
    images: SnapshotStateList<ImageLoadState>,
    mediaViewerScreenState: MediaViewerScreenState,
    pagerState: PagerState,
    toolbarHeight: Dp,
    mpvSettings: MpvSettings,
    mpvLibsInstalledAndLoaded: Lazy<Boolean>,
    videoMediaState: VideoMediaState?
  ) {
    val postImageDataLoadState = images[page]

    DisposableEffect(
      key1 = Unit,
      effect = {
        // When a page with media is being disposed we need to replace the current ImageLoadState
        // with ImageLoadState.PreparingForLoading so that when we go back to this page we reload
        // it again from cache/network. Otherwise we may use the cached ImageLoadState and while
        // it might it set to ImageLoadState.Ready in reality the file on disk may long be
        // removed so it will cause a crash.
        onDispose {
          val currentImages = mediaViewerScreenState.images
            ?: return@onDispose

          val indexOfThisImage = currentImages.indexOfFirst { it.fullImageUrl == postImageDataLoadState.fullImageUrl }
          if (indexOfThisImage >= 0) {
            val prevPostImageData = currentImages[indexOfThisImage].postImageData
            currentImages.set(indexOfThisImage, ImageLoadState.PreparingForLoading(prevPostImageData))
          }
        }
      }
    )

    val displayImagePreviewMovable = remember {
      movableContentOf {
        DisplayImagePreview(postImageDataLoadState)
      }
    }

    val coroutineScope = rememberCoroutineScope()

    when (postImageDataLoadState) {
      is ImageLoadState.PreparingForLoading -> {
        var loadingProgressMut by remember { mutableStateOf<Pair<Int, Float>?>(null) }

        LoadFullImage(
          mediaViewerScreenState = mediaViewerScreenState,
          postImageDataLoadState = postImageDataLoadState,
          onLoadProgressUpdated = { restartIndex, progress ->
            loadingProgressMut = Pair(restartIndex, progress)
          }
        )

        displayImagePreviewMovable()

        val loadingProgress = loadingProgressMut
        if (loadingProgress != null) {
          val restartIndex = loadingProgress.first
          val progress = loadingProgress.second

          DisplayLoadingProgressIndicator(restartIndex, progress)
        }
      }
      is ImageLoadState.Progress -> {
        // no-op
      }
      is ImageLoadState.Ready -> {
        var fullMediaLoaded by remember { mutableStateOf(false) }
        if (!fullMediaLoaded) {
          displayImagePreviewMovable()
        }

        when (postImageDataLoadState.postImageData.imageType()) {
          ImageType.Static -> {
            val imageFile = checkNotNull(postImageDataLoadState.imageFile) { "Can't stream static images" }

            DisplayFullImage(
              postImageDataLoadState = postImageDataLoadState,
              imageFile = imageFile,
              onFullImageLoaded = { fullMediaLoaded = true },
              onFullImageFailedToLoad = { fullMediaLoaded = false },
              onImageTapped = { coroutineScope.launch { onBackPressed() } }
            )
          }
          ImageType.Video -> {
            DisplayVideo(
              pageIndex = page,
              pagerState = pagerState,
              toolbarHeight = toolbarHeight,
              mpvSettings = mpvSettings,
              postImageDataLoadState = postImageDataLoadState,
              snackbarManager = snackbarManager,
              checkLibrariesInstalledAndLoaded = { mpvLibsInstalledAndLoaded.value },
              onPlayerLoaded = { fullMediaLoaded = true },
              onPlayerUnloaded = { fullMediaLoaded = false },
              videoMediaState = videoMediaState,
              onVideoTapped = { coroutineScope.launch { onBackPressed() } },
              installMpvLibsFromGithubButtonClicked = {
                onInstallMpvLibsFromGithubButtonClicked(
                  mpvSettings = mpvSettings,
                  context = context
                )
              }
            )
          }
          ImageType.Unsupported -> {
            DisplayUnsupportedMedia(
              toolbarHeight = toolbarHeight,
              postImageDataLoadState = postImageDataLoadState,
              onFullImageLoaded = { fullMediaLoaded = true },
              onFullImageFailedToLoad = { fullMediaLoaded = false },
              onImageTapped = { coroutineScope.launch { onBackPressed() } }
            )
          }
        }
      }
      is ImageLoadState.Error -> {
        DisplayImageLoadError(
          toolbarHeight = toolbarHeight,
          postImageDataLoadState = postImageDataLoadState
        )
      }
    }
  }

  private fun onInstallMpvLibsFromGithubButtonClicked(
    mpvSettings: MpvSettings,
    context: Context
  ) {
    val progressScreen = ProgressScreen(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      title = context.resources.getString(R.string.media_viewer_plugins_loading_libs)
    )

    mediaViewerScreenViewModel.installMpvLibsFromGithub(
      mpvSettings = mpvSettings,
      showLoading = { navigationRouter.presentScreen(progressScreen) },
      hideLoading = { navigationRouter.stopPresentingScreen(progressScreen.screenKey) },
      onFailed = { error ->
        val dialogScreen = DialogScreen(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          params = DialogScreen.Params(
            title = DialogScreen.Text.Id(R.string.media_viewer_plugins_libs_installation_failure),
            description = DialogScreen.Text.String(
              context.resources.getString(
                R.string.media_viewer_plugins_libs_installation_description_failure,
                error.errorMessageOrClassName()
              )
            ),
            positiveButton = DialogScreen.okButton()
          )
        )

        navigationRouter.presentScreen(dialogScreen)
      },
      onSuccess = {
        val dialogScreen = DialogScreen(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          params = DialogScreen.Params(
            title = DialogScreen.Text.Id(R.string.media_viewer_plugins_libs_installation_success),
            description = DialogScreen.Text.Id(R.string.media_viewer_plugins_libs_installation_description_success),
            positiveButton = DialogScreen.okButton(
              onClick = { androidHelpers.restartApp(context as Activity) }
            )
          )
        )

        navigationRouter.presentScreen(dialogScreen)
      }
    )
  }

  @Composable
  private fun LoadFullImage(
    mediaViewerScreenState: MediaViewerScreenState,
    postImageDataLoadState: ImageLoadState.PreparingForLoading,
    onLoadProgressUpdated: (Int, Float) -> Unit
  ) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(
      key1 = postImageDataLoadState.postImageData.fullImageAsUrl,
      block = {
        val retriesCount = AtomicInteger(0)

        loadFullImageInternal(
          appContenxt = context.applicationContext,
          coroutineScope = coroutineScope,
          retriesCount = retriesCount,
          postImageDataLoadState = postImageDataLoadState,
          mediaViewerScreenState = mediaViewerScreenState,
          onLoadProgressUpdated = onLoadProgressUpdated
        )
      }
    )
  }

  private suspend fun loadFullImageInternal(
    appContenxt: Context,
    coroutineScope: CoroutineScope,
    retriesCount: AtomicInteger,
    postImageDataLoadState: ImageLoadState.PreparingForLoading,
    mediaViewerScreenState: MediaViewerScreenState,
    onLoadProgressUpdated: (Int, Float) -> Unit
  ) {
    val postImageData = postImageDataLoadState.postImageData
    val fullImageUrl = postImageData.fullImageAsUrl

    val index = mediaViewerScreenState.requireImages().indexOfFirst { imageLoadState ->
      imageLoadState.fullImageUrl == postImageData.fullImageAsUrl
    }

    // We can just stream videos without having to load the first
    if (postImageDataLoadState.postImageData.imageType() == ImageType.Video) {
      mediaViewerScreenState.requireImages().set(index, ImageLoadState.Ready(postImageData, null))
      return
    }

    if (index < 0) {
      val exception = MediaViewerScreenViewModel.ImageLoadException(
        fullImageUrl,
        appContenxt.getString(R.string.media_viewer_failed_to_find_image_in_images)
      )

      val imageLoadState = ImageLoadState.Error(postImageData, exception)
      mediaViewerScreenState.requireImages().set(index, imageLoadState)

      return
    }

    mediaViewerScreenViewModel.loadFullImageAndGetFile(postImageDataLoadState.postImageData)
      .collect { imageLoadState ->
        when (imageLoadState) {
          is ImageLoadState.Progress -> {
            onLoadProgressUpdated(retriesCount.get(), imageLoadState.progress)
          }
          is ImageLoadState.Error -> {
            val canRetry = imageLoadState.exception is IOException
            if (canRetry) {
              val currentRetryIndex = retriesCount.incrementAndGet()
              if (currentRetryIndex <= MediaViewerScreenViewModel.MAX_RETRIES) {
                coroutineScope.launch {
                  logcat { "Got retriable Error state, waiting ${currentRetryIndex} seconds and then restarting image load" }
                  delay(currentRetryIndex * 1000L)

                  loadFullImageInternal(
                    appContenxt = appContenxt,
                    coroutineScope = coroutineScope,
                    retriesCount = retriesCount,
                    postImageDataLoadState = postImageDataLoadState,
                    mediaViewerScreenState = mediaViewerScreenState,
                    onLoadProgressUpdated = onLoadProgressUpdated
                  )
                }

                return@collect
              }

              // fallthrough
            }

            mediaViewerScreenState.requireImages().set(index, imageLoadState)
          }
          is ImageLoadState.PreparingForLoading,
          is ImageLoadState.Ready -> {
            mediaViewerScreenState.requireImages().set(index, imageLoadState)
          }
        }
      }
  }

  @Composable
  private fun DisplayImageLoadError(
    toolbarHeight: Dp,
    postImageDataLoadState: ImageLoadState.Error
  ) {
    val additionalPaddings = remember(toolbarHeight) { PaddingValues(top = toolbarHeight) }

    InsetsAwareBox(
      modifier = Modifier.fillMaxSize(),
      additionalPaddings = additionalPaddings
    ) {
      // TODO(KurobaEx):
      KurobaComposeText(text = "Error: ${postImageDataLoadState.exception.errorMessageOrClassName()}")
    }
  }

  @Composable
  private fun DisplayImagePreview(postImageDataState: ImageLoadState) {
    val postImageDataLoadState = (postImageDataState as? ImageLoadState.PreparingForLoading)
      ?: return

    val context = LocalContext.current
    val postImageData = postImageDataLoadState.postImageData

    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(context)
        .data(postImageData.thumbnailAsUrl)
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