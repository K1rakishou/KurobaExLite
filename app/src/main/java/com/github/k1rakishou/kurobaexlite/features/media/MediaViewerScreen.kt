package com.github.k1rakishou.kurobaexlite.features.media

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.withTranslation
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.DisplayLoadingProgressIndicator
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPreviewStrip
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerScreenVideoControls
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerToolbar
import com.github.k1rakishou.kurobaexlite.features.media.media_handlers.DisplayFullImage
import com.github.k1rakishou.kurobaexlite.features.media.media_handlers.DisplayUnsupportedMedia
import com.github.k1rakishou.kurobaexlite.features.media.media_handlers.DisplayVideo
import com.github.k1rakishou.kurobaexlite.features.media.media_handlers.VideoMediaState
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.RuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.model.data.originalFileNameWithExtension
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalRuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.progress.ProgressScreen
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject


class MediaViewerScreen(
  private val mediaViewerParams: MediaViewerParams,
  private val openedFromScreen: ScreenKey,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val mediaViewerScreenViewModel: MediaViewerScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val mediaViewerPostListScroller: MediaViewerPostListScroller by inject(MediaViewerPostListScroller::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY
  override val unpresentAnimation: NavigationRouter.ScreenRemoveAnimation = NavigationRouter.ScreenRemoveAnimation.Pop

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun CardContent() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val coroutineScope = rememberCoroutineScope()
    val mediaViewerScreenState = remember { MediaViewerScreenState() }
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    var clickedThumbnailBounds by remember { mutableStateOf(clickedThumbnailBoundsStorage.getBounds()) }
    var transitionFinished by remember { mutableStateOf(false) }
    var previewLoadingFinished by remember { mutableStateOf(false) }

    val animatable = remember { Animatable(0f) }
    val animationProgress by animatable.asState()

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = animationProgress }
        .background(Color.Black)
    ) {
      InitMediaViewerData(
        mediaViewerScreenState = mediaViewerScreenState,
        mediaViewerParams = mediaViewerParams
      )

      Box(
        modifier = Modifier.graphicsLayer {
          alpha = if (transitionFinished && previewLoadingFinished) 1f else 0f
        }
      ) {
        ContentAfterTransition(
          mediaViewerScreenState = mediaViewerScreenState,
          chanTheme = chanTheme,
          toolbarHeight = toolbarHeight,
          coroutineScope = coroutineScope,
          insets = insets,
          onPreviewLoadingFinished = { postImage ->
            if (clickedThumbnailBounds == null || postImage == clickedThumbnailBounds?.postImage) {
              previewLoadingFinished = true
            }
          }
        )
      }

      TransitionPreview(
        animatable = animatable,
        clickedThumbnailBounds = clickedThumbnailBounds,
        onTransitionFinished = {
          if (!transitionFinished) {
            animatable.snapTo(1f)
            transitionFinished = true
          }
        }
      )

      LaunchedEffect(
        key1 = transitionFinished,
        key2 = previewLoadingFinished,
        block = {
          if (transitionFinished && previewLoadingFinished && clickedThumbnailBounds != null) {
            clickedThumbnailBounds = null
            clickedThumbnailBoundsStorage.consumeBounds()
          }
        }
      )
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun BoxScope.ContentAfterTransition(
    mediaViewerScreenState: MediaViewerScreenState,
    chanTheme: ChanTheme,
    toolbarHeight: Dp,
    coroutineScope: CoroutineScope,
    insets: Insets,
    onPreviewLoadingFinished: (IPostImage) -> Unit
  ) {
    var pagerStateHolderMut by remember { mutableStateOf<PagerState?>(null) }
    val pagerStateHolder = pagerStateHolderMut

    val toolbarTotalHeight = remember { mutableStateOf<Int?>(null) }
    val globalMediaViewerControlsVisible by remember { mutableStateOf(true) }
    val images = mediaViewerScreenState.images

    val bgColor = remember(chanTheme.primaryColorCompose) {
      chanTheme.primaryColorCompose.copy(alpha = 0.5f)
    }

    val videoMediaState = remember(key1 = pagerStateHolder?.currentPage) {
      val currentPage = pagerStateHolder?.currentPage
        ?: return@remember null

      val videoControlsVisible = mediaViewerScreenState.images
        ?.getOrNull(currentPage)
        ?.postImage
        ?.imageType() == ImageType.Video

      return@remember VideoMediaState(currentPage, videoControlsVisible)
    }

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose { mediaViewerScreenViewModel.destroy() }
      }
    )

    MediaViewerPager(
      toolbarHeight = toolbarHeight,
      mediaViewerScreenState = mediaViewerScreenState,
      onViewPagerInitialized = { pagerState -> pagerStateHolderMut = pagerState },
      videoMediaState = videoMediaState,
      onPreviewLoadingFinished = onPreviewLoadingFinished
    )

    if (pagerStateHolder == null || videoMediaState == null) {
      return
    }

    Box(
      modifier = Modifier.Companion
        .align(Alignment.TopCenter)
        .background(bgColor)
        .onSizeChanged { size -> toolbarTotalHeight.value = size.height }
    ) {
      val context = LocalContext.current
      val runtimePermissionsHelper = LocalRuntimePermissionsHelper.current

      MediaViewerToolbar(
        toolbarHeight = toolbarHeight,
        screenKey = screenKey,
        mediaViewerScreenState = mediaViewerScreenState,
        pagerState = pagerStateHolder,
        onDownloadMediaClicked = { postImage ->
          onDownloadButtonClicked(
            context = context,
            runtimePermissionsHelper = runtimePermissionsHelper,
            postImage = postImage
          )
        },
        onBackPressed = { coroutineScope.launch { onBackPressed() } }
      )
    }

    val videoControlsVisible by videoMediaState.videoControlsVisibleState

    if (mediaViewerScreenViewModel.mpvInitialized) {
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
        MediaViewerScreenVideoControls(videoMediaState)

        Spacer(modifier = Modifier.height(insets.bottom))
      }
    }

    if (images.isNotNullNorEmpty()) {
      val toolbarCalculatedHeight by toolbarTotalHeight

      val actualToolbarCalculatedHeight = remember(
        key1 = toolbarCalculatedHeight,
        key2 = globalMediaViewerControlsVisible,
      ) {
        if (toolbarCalculatedHeight == null || !globalMediaViewerControlsVisible) {
          return@remember null
        }

        return@remember toolbarCalculatedHeight
      }

      if (toolbarCalculatedHeight != null) {
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
        ) {
          MediaViewerPreviewStrip(
            pagerState = pagerStateHolder,
            images = images,
            bgColor = bgColor,
            toolbarHeightPx = actualToolbarCalculatedHeight,
            uiInfoManager = uiInfoManager,
            onPreviewClicked = { postImage ->
              coroutineScope.launch {
                images
                  .indexOfFirst { it.fullImageUrl == postImage.fullImageAsUrl }
                  .takeIf { index -> index >= 0 }
                  ?.let { scrollIndex -> pagerStateHolder.scrollToPage(scrollIndex) }
              }
            }
          )
        }
      }
    }
  }

  private fun onDownloadButtonClicked(
    context: Context,
    runtimePermissionsHelper: RuntimePermissionsHelper,
    postImage: IPostImage
  ) {
    // On Q and above we use MediaStore which does not require us requesting the permission
    if (
      androidHelpers.isAndroidQ() ||
      runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    ) {
      downloadMediaActual(context, postImage)
      return
    }

    runtimePermissionsHelper.requestPermission(
      permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
      callback = object : RuntimePermissionsHelper.Callback {
        override fun onRuntimePermissionResult(granted: Boolean) {
          if (granted) {
            downloadMediaActual(context, postImage)
            return
          }

          val message = context.resources.getString(R.string.media_viewer_download_failed_no_permission)
          snackbarManager.toast(message = message, screenKey = MainScreen.SCREEN_KEY)
        }
      }
    )
  }

  private fun downloadMediaActual(context: Context, postImage: IPostImage) {
    mediaViewerScreenViewModel.downloadMedia(
      postImage = postImage,
      onResult = { mediaDownloadResult ->
        if (mediaDownloadResult.isSuccess) {
          val message = context.resources.getString(
            R.string.media_viewer_download_success,
            postImage.originalFileNameWithExtension()
          )

          snackbarManager.toast(message = message, screenKey = MainScreen.SCREEN_KEY)
        } else {
          val error = mediaDownloadResult.exceptionOrThrow()
          val message = context.resources.getString(
            R.string.media_viewer_download_failed,
            postImage.originalFileNameWithExtension(),
            error.errorMessageOrClassName()
          )

          snackbarManager.toast(message = message, screenKey = MainScreen.SCREEN_KEY)
        }
      }
    )
  }

  @Composable
  private fun TransitionPreview(
    animatable: Animatable<Float, AnimationVector1D>,
    clickedThumbnailBounds: ClickedThumbnailBoundsStorage.ClickedThumbnailBounds?,
    onTransitionFinished: suspend () -> Unit
  ) {
    if (clickedThumbnailBounds == null) {
      LaunchedEffect(
        key1 = Unit,
        block = { onTransitionFinished() }
      )

      return
    }

    val bitmapMatrix = remember { Matrix() }
    val postImage = clickedThumbnailBounds.postImage
    val srcBounds = clickedThumbnailBounds.bounds

    val bitmapPaint = remember {
      Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
      }
    }

    var bitmapMut by remember { mutableStateOf<Bitmap?>(null) }
    val bitmap = bitmapMut
    val context = LocalContext.current.applicationContext

    LaunchedEffect(
      key1 = Unit,
      block = {
        var success = false

        try {
          withTimeout(timeMillis = 1000) {
            bitmapMut = mediaViewerScreenViewModel.loadThumbnailBitmap(context, postImage)
          }

          success = true
        } catch (error: Throwable) {
          logcatError { "error: ${error.errorMessageOrClassName()}" }
          success = false
        } finally {
          if (!success) {
            onTransitionFinished()
          }
        }
      }
    )

    if (bitmap == null) {
      return
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        try {
          animatable.animateTo(1f, animationSpec = tween(250))
        } finally {
          onTransitionFinished()
        }
      }
    )

    val animationProgress by animatable.asState()

    Canvas(
      modifier = Modifier.fillMaxSize(),
      onDraw = {
        val nativeCanvas = drawContext.canvas.nativeCanvas

        val scale = Math.min(
          nativeCanvas.width.toFloat() / bitmap.width,
          nativeCanvas.height.toFloat() / bitmap.height
        )
        val dstWidth = bitmap.width * scale
        val dstHeight = bitmap.height * scale

        bitmapMatrix.reset()
        bitmapMatrix.setScale(
          lerpFloat(1f, scale, animationProgress),
          lerpFloat(1f, scale, animationProgress)
        )

        val startY = srcBounds.top
        val endY = (nativeCanvas.height.toFloat() - dstHeight) / 2f

        val startX = srcBounds.left
        val endX = (nativeCanvas.width.toFloat() - dstWidth) / 2f

        nativeCanvas.withTranslation(
          x = lerpFloat(startX, endX, animationProgress),
          y = lerpFloat(startY, endY, animationProgress)
        ) {
          nativeCanvas.drawBitmap(bitmap, bitmapMatrix, bitmapPaint)
        }
      }
    )
  }

  @Composable
  private fun InitMediaViewerData(
    mediaViewerScreenState: MediaViewerScreenState,
    mediaViewerParams: MediaViewerParams
  ) {
    when (mediaViewerParams) {
      is MediaViewerParams.Catalog,
      is MediaViewerParams.Thread -> {
        val postsAsyncDataState by if (mediaViewerParams is MediaViewerParams.Catalog) {
          catalogScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
        } else {
          threadScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
        }

        val postCellDataStateListMut = (postsAsyncDataState as? AsyncData.Data)?.data?.posts
        val postCellDataStateList = postCellDataStateListMut

        LaunchedEffect(
          key1 = postCellDataStateList,
          block = {
            if (postCellDataStateList == null) {
              mediaViewerScreenState.images = null
              mediaViewerScreenState.initialPage.value = null

              return@LaunchedEffect
            }

            val initResult = mediaViewerScreenViewModel.initFromPostStateList(
              postCellDataStateList = postCellDataStateList,
              initialImageUrl = mediaViewerParams.initialImage
            )

            Snapshot.withMutableSnapshot {
              val images = mutableStateListOf<ImageLoadState>()
              images.addAll(initResult.images)

              mediaViewerScreenState.images = images
              mediaViewerScreenState.initialPage.value = initResult.initialPage
            }
          }
        )
      }
      is MediaViewerParams.Images -> {
        LaunchedEffect(
          key1 = Unit,
          block = {
            val initResult = mediaViewerScreenViewModel.initFromImageList(
              images = mediaViewerParams.images,
              initialImageUrl = mediaViewerParams.initialImageUrl
            )

            Snapshot.withMutableSnapshot {
              val images = mutableStateListOf<ImageLoadState>()
              images.addAll(initResult.images)

              mediaViewerScreenState.images = images
              mediaViewerScreenState.initialPage.value = initResult.initialPage
            }
          }
        )
      }
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun MediaViewerPager(
    toolbarHeight: Dp,
    mediaViewerScreenState: MediaViewerScreenState,
    onViewPagerInitialized: (PagerState) -> Unit,
    videoMediaState: VideoMediaState?,
    onPreviewLoadingFinished: (IPostImage) -> Unit
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

        val postImageData = images.getOrNull(pagerState.currentPage)?.postImage
          ?: return@LaunchedEffect

        mediaViewerPostListScroller.onSwipedTo(
          screenKey = openedFromScreen,
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
        page = page,
        images = images,
        mediaViewerScreenState = mediaViewerScreenState,
        pagerState = pagerState,
        toolbarHeight = toolbarHeight,
        mpvSettings = mpvSettings,
        checkLibrariesInstalledAndLoaded = { mpvLibsInstalledAndLoaded.value },
        videoMediaState = videoMediaState,
        onPreviewLoadingFinished = onPreviewLoadingFinished
      )
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun PagerContent(
    page: Int,
    images: MutableList<ImageLoadState>,
    mediaViewerScreenState: MediaViewerScreenState,
    pagerState: PagerState,
    toolbarHeight: Dp,
    mpvSettings: MpvSettings,
    checkLibrariesInstalledAndLoaded: () -> Boolean,
    videoMediaState: VideoMediaState?,
    onPreviewLoadingFinished: (IPostImage) -> Unit
  ) {
    val context = LocalContext.current
    val postImageDataLoadState = images[page]

    DisposableEffect(
      key1 = Unit,
      effect = {
        // When a page with media is being disposed we need to replace the current ImageLoadState
        // with ImageLoadState.PreparingForLoading so that when we go back to this page we reload
        // it again from cache/network. Otherwise we may use the cached ImageLoadState and while
        // it might be set to "ImageLoadState.Ready" in reality the file on disk may long be
        // removed so it will cause a crash.
        onDispose {
          val indexOfThisImage = images.indexOfFirst { it.fullImageUrl == postImageDataLoadState.fullImageUrl }
          if (indexOfThisImage >= 0) {
            val prevPostImageData = images[indexOfThisImage].postImage
            images.set(indexOfThisImage, ImageLoadState.PreparingForLoading(prevPostImageData))
          }
        }
      }
    )

    val displayImagePreviewMovable = remember {
      movableContentOf {
        DisplayImagePreview(
          postImageDataState = postImageDataLoadState,
          onPreviewLoadingFinished = onPreviewLoadingFinished
        )
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

        when (postImageDataLoadState.postImage.imageType()) {
          ImageType.Static -> {
            val imageFile = checkNotNull(postImageDataLoadState.imageFile) { "Can't stream static images" }

            DisplayFullImage(
              postImageDataLoadState = postImageDataLoadState,
              imageFile = imageFile,
              onFullImageLoaded = { fullMediaLoaded = true },
              onFullImageFailedToLoad = { fullMediaLoaded = false },
              onImageTapped = { coroutineScope.launch { onBackPressed() } },
              reloadImage = {
                coroutineScope.launch {
                  mediaViewerScreenViewModel.removeFileFromDisk(postImageDataLoadState.postImage)
                  images[page] = ImageLoadState.PreparingForLoading(postImageDataLoadState.postImage)
                }
              }
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
              checkLibrariesInstalledAndLoaded = checkLibrariesInstalledAndLoaded,
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
      key1 = postImageDataLoadState.postImage.fullImageAsUrl,
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
    val postImageData = postImageDataLoadState.postImage
    val fullImageUrl = postImageData.fullImageAsUrl

    val index = mediaViewerScreenState.requireImages().indexOfFirst { imageLoadState ->
      imageLoadState.fullImageUrl == postImageData.fullImageAsUrl
    }

    // We can just stream videos without having to load the first
    if (postImageDataLoadState.postImage.imageType() == ImageType.Video) {
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

    mediaViewerScreenViewModel.loadFullImageAndGetFile(postImageDataLoadState.postImage)
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
  private fun DisplayImagePreview(
    postImageDataState: ImageLoadState,
    onPreviewLoadingFinished: (IPostImage) -> Unit
  ) {
    val postImageDataLoadState = (postImageDataState as? ImageLoadState.PreparingForLoading)
      ?: return

    val context = LocalContext.current
    val postImageData = postImageDataLoadState.postImage

    var callbackCalled by remember { mutableStateOf(false) }

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

        LaunchedEffect(
          key1 = state,
          block = {
            if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
              if (!callbackCalled) {
                callbackCalled = true
                onPreviewLoadingFinished(postImageData)
              }
            }
          }
        )
      }
    )
  }

  class MediaViewerScreenState {
    var images: SnapshotStateList<ImageLoadState>? = null
    val initialPage = mutableStateOf<Int?>(null)

    fun isLoaded(): Boolean = initialPage.value != null && images != null
    fun requireImages(): SnapshotStateList<ImageLoadState> = requireNotNull(images) { "images not initialized yet!" }
  }

  companion object {
    private const val TAG = "MediaViewerScreen"
    val SCREEN_KEY = ScreenKey("MediaViewerScreen")
  }
}