package com.github.k1rakishou.kurobaexlite.features.media

import android.Manifest
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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withTranslation
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.DisplayLoadingProgressIndicator
import com.github.k1rakishou.kurobaexlite.features.media.helpers.DraggableArea
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPreviewStrip
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerScreenVideoControls
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerToolbar
import com.github.k1rakishou.kurobaexlite.features.media.media_handlers.DisplayFullImage
import com.github.k1rakishou.kurobaexlite.features.media.media_handlers.DisplayUnsupportedMedia
import com.github.k1rakishou.kurobaexlite.features.media.media_handlers.DisplayVideo
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppRestarter
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalRuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.MinimizableFloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.passClicksThrough
import com.github.k1rakishou.kurobaexlite.ui.helpers.progress.ProgressScreen
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject


class MediaViewerScreen(
  private val mediaViewerParams: MediaViewerParams,
  private val openedFromScreen: ScreenKey,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : MinimizableFloatingComposeScreen(componentActivity, navigationRouter) {
  private val mediaViewerScreenViewModel: MediaViewerScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val mediaViewerPostListScroller: MediaViewerPostListScroller by inject(MediaViewerPostListScroller::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val appRestarter: AppRestarter by inject(AppRestarter::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val defaultIsDragGestureAllowedFunc: (currPosition: Offset, startPosition: Offset) -> Boolean = { _, _ -> true }

  override val customBackground: Boolean = true
  override val screenKey: ScreenKey = SCREEN_KEY
  override val isScreenMinimized: MutableState<Boolean> = mediaViewerScreenViewModel.isScreenMinimized

  override val unpresentAnimation: NavigationRouter.ScreenRemoveAnimation
    get() {
      return if (isScreenMinimized.value) {
        NavigationRouter.ScreenRemoveAnimation.FadeOut
      } else {
        NavigationRouter.ScreenRemoveAnimation.Pop
      }
    }

  override fun onDisposed() {
    super.onDisposed()

    mediaViewerScreenViewModel.destroy()
  }

  @Composable
  override fun CardContent() {
    val mediaViewerScreenState =
      rememberSaveable(saver = MediaViewerScreenState.Saver(appSettings)) {
        MediaViewerScreenState(appSettings)
      }

    MinimizableContent(
      onCloseMediaViewerClicked = { stopPresenting() },
      goToPreviousMedia = { mediaViewerScreenState.goToPrevMedia() },
      goToNextMedia = { mediaViewerScreenState.goToNextMedia() },
      togglePlayPause = { },
      isCurrentlyPaused = { false },
      content = { isMinimized ->
        CardContentInternal(
          isMinimized = isMinimized,
          mediaViewerScreenState = mediaViewerScreenState
        )
      }
    )
  }

  @Composable
  private fun CardContentInternal(
    isMinimized: Boolean,
    mediaViewerScreenState: MediaViewerScreenState
  ) {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val coroutineScope = rememberCoroutineScope()
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    var clickedThumbnailBounds by remember { mutableStateOf(clickedThumbnailBoundsStorage.getBounds()) }
    var transitionFinished by remember { mutableStateOf(false) }
    var previewLoadingFinished by remember { mutableStateOf(false) }

    val animatable = remember { Animatable(0f) }
    val animationProgress by animatable.asState()

    var availableSizeMut by remember(key1 = isMinimized) { mutableStateOf<IntSize>(IntSize.Zero) }
    val availableSize = availableSizeMut

    val bgColor = if (isMinimized) {
      Color.Unspecified
    } else {
      Color.Black
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = animationProgress }
        .background(bgColor)
        .onSizeChanged { newSize -> availableSizeMut = newSize }
    ) {
      InitMediaViewerData(
        mediaViewerScreenState = mediaViewerScreenState,
        mediaViewerParams = mediaViewerParams
      )

      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            alpha = if (transitionFinished && previewLoadingFinished) 1f else 0f
          }
      ) {
        if (availableSize.width > 0 && availableSize.height > 0) {
          ContentAfterTransition(
            isMinimized = isMinimized,
            mediaViewerScreenState = mediaViewerScreenState,
            chanTheme = chanTheme,
            availableSize = availableSize,
            toolbarHeight = toolbarHeight,
            coroutineScope = coroutineScope,
            insets = insets,
            onPreviewLoadingFinished = { postImage ->
              val previewLoadingFinishedForOutThumbnail = clickedThumbnailBounds == null
                || postImage == clickedThumbnailBounds?.postImage

              logcat(TAG, LogPriority.VERBOSE) {
                "onPreviewLoadingFinished() " +
                  "expected '${clickedThumbnailBounds?.postImage?.fullImageAsString}', " +
                  "got '${postImage.fullImageAsString}', " +
                  "previewLoadingFinishedForOutThumbnail=${previewLoadingFinishedForOutThumbnail}"
              }

              if (previewLoadingFinishedForOutThumbnail) {
                previewLoadingFinished = true
              }
            }
          )
        }
      }

      TransitionPreview(
        animatable = animatable,
        clickedThumbnailBounds = clickedThumbnailBounds,
        onTransitionFinished = {
          if (!transitionFinished) {
            logcat(TAG, LogPriority.VERBOSE) {
              "onTransitionFinished() for image '${clickedThumbnailBounds?.postImage?.fullImageAsString}'"
            }

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
    isMinimized: Boolean,
    mediaViewerScreenState: MediaViewerScreenState,
    chanTheme: ChanTheme,
    availableSize: IntSize,
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

    val bgColor = remember(chanTheme.backColor) {
      chanTheme.backColor.copy(alpha = 0.5f)
    }

    MediaViewerPagerContainer(
      isMinimized = isMinimized,
      availableSize = availableSize,
      toolbarHeight = toolbarHeight,
      mediaViewerScreenState = mediaViewerScreenState,
      onViewPagerInitialized = { pagerState -> pagerStateHolderMut = pagerState },
      onPreviewLoadingFinished = onPreviewLoadingFinished
    )

    if (pagerStateHolder == null || images.isEmpty()) {
      return
    }

    val mediaViewerUiVisible by mediaViewerScreenState.mediaViewerUiVisible
    val mediaViewerUiAlpha by animateFloatAsState(
      targetValue = if (mediaViewerUiVisible) 1f else 0f,
      animationSpec = tween(durationMillis = 300)
    )

    if (!isMinimized) {
      Box(
        modifier = Modifier.Companion
          .align(Alignment.TopCenter)
          .alpha(mediaViewerUiAlpha)
          .passClicksThrough(passClicks = !mediaViewerUiVisible)
          .onSizeChanged { size -> toolbarTotalHeight.value = size.height }
          .background(bgColor)
      ) {
        val context = LocalContext.current
        val runtimePermissionsHelper = LocalRuntimePermissionsHelper.current

        MediaViewerToolbar(
          toolbarHeight = toolbarHeight,
          backgroundColor = bgColor,
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
          onMinimizeClicked = { minimize() },
          onBackPressed = { coroutineScope.launch { onBackPressed() } }
        )
      }
    }

    val currentPageIndex by remember { derivedStateOf { pagerStateHolder.currentPage } }
    val currentLoadedMedia = remember(key1 = currentPageIndex) {
      mediaViewerScreenState.currentlyLoadedMediaMap[currentPageIndex]
    }

    val targetAlpha = if (
      mediaViewerScreenViewModel.mpvInitialized &&
      currentLoadedMedia is MediaState.Video &&
      mediaViewerUiVisible
    ) {
      1f
    } else {
      0f
    }

    val alphaAnimated by animateFloatAsState(
      targetValue = targetAlpha,
      animationSpec = tween(durationMillis = 300)
    )

    if (!isMinimized) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .passClicksThrough(passClicks = !mediaViewerUiVisible)
          .graphicsLayer { this.alpha = alphaAnimated }
          .background(bgColor)
      ) {
        if (currentLoadedMedia is MediaState.Video) {
          MediaViewerScreenVideoControls(currentLoadedMedia)
        }

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

      if (!isMinimized && toolbarCalculatedHeight != null) {
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .alpha(mediaViewerUiAlpha)
            .passClicksThrough(passClicks = !mediaViewerUiVisible)
        ) {
          MediaViewerPreviewStrip(
            pagerState = pagerStateHolder,
            images = images,
            bgColor = bgColor,
            toolbarHeightPx = actualToolbarCalculatedHeight,
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
        block = {
          logcat(TAG, LogPriority.VERBOSE) { "loadThumbnailBitmap() clickedThumbnailBounds == null" }
          onTransitionFinished()
        }
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
          logcatError(TAG) { "loadThumbnailBitmap() error: ${error.errorMessageOrClassName()}" }
          success = false
        } finally {
          if (!success) {
            logcat(TAG, LogPriority.VERBOSE) { "loadThumbnailBitmap() success: false" }
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
          logcat(TAG, LogPriority.VERBOSE) { "loadThumbnailBitmap() success: true" }
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

        val startX = srcBounds.left
        val endX = (nativeCanvas.width.toFloat() - dstWidth) / 2f

        val startY = srcBounds.top
        val endY = (nativeCanvas.height.toFloat() - dstHeight) / 2f

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

        val postCellDataListMut = (postsAsyncDataState as? AsyncData.Data)?.data?.posts
        val postCellDataList = postCellDataListMut

        LaunchedEffect(
          key1 = postCellDataList,
          block = {
            if (postCellDataList == null) {
              mediaViewerScreenState.init(
                images = null,
                initialPage = null,
                mediaViewerUiVisible = appSettings.mediaViewerUiVisible.read()
              )

              return@LaunchedEffect
            }

            val initResult = mediaViewerScreenViewModel.initFromPostStateList(
              postCellDataList = postCellDataList,
              initialImageUrl = mediaViewerParams.initialImage
            )

            mediaViewerScreenState.init(
              images = initResult.images,
              initialPage = initResult.initialPage,
              mediaViewerUiVisible = appSettings.mediaViewerUiVisible.read()
            )
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

            mediaViewerScreenState.init(
              images = initResult.images,
              initialPage = initResult.initialPage,
              mediaViewerUiVisible = appSettings.mediaViewerUiVisible.read()
            )
          }
        )
      }
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun MediaViewerPagerContainer(
    isMinimized: Boolean,
    availableSize: IntSize,
    toolbarHeight: Dp,
    mediaViewerScreenState: MediaViewerScreenState,
    onViewPagerInitialized: (PagerState) -> Unit,
    onPreviewLoadingFinished: (IPostImage) -> Unit
  ) {
    val context = LocalContext.current
    val initialPageMut by mediaViewerScreenState.initialPage
    val imagesMut = mediaViewerScreenState.images

    val initialPage = initialPageMut
    val images = imagesMut

    if (initialPage == null || images.isEmpty()) {
      return
    }

    if (images.isEmpty()) {
      val additionalPaddings = remember(toolbarHeight) { PaddingValues(top = toolbarHeight) }

      InsetsAwareBox(
        modifier = Modifier.fillMaxSize(),
        additionalPaddings = additionalPaddings
      ) {
        KurobaComposeText(text = stringResource(id = R.string.media_viewer_no_images_to_show))
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

    var initialScrollHappened by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState()
    val currentPageIndex by remember { derivedStateOf { pagerState.currentPage } }

    LaunchedEffect(
      key1 = Unit,
      block = {
        mediaViewerScreenState.mediaNavigationEventFlow.collectLatest { mediaNavigationEvent ->
          val currentPage = pagerState.currentPage
          val pageCount = pagerState.pageCount

          var nextPage = when (mediaNavigationEvent) {
            MediaViewerScreenState.MediaNavigationEvent.GoToPrev -> currentPage - 1
            MediaViewerScreenState.MediaNavigationEvent.GoToNext -> currentPage + 1
          }

          if (nextPage < 0) {
            nextPage = (pageCount - 1)
          }

          nextPage %= pageCount

          if ((nextPage - currentPage).absoluteValue > 1) {
            pagerState.scrollToPage(nextPage)
          } else {
            pagerState.animateScrollToPage(nextPage)
          }
        }
      }
    )

    LaunchedEffect(
      key1 = pagerState,
      block = {
        try {
          pagerState.scrollToPage(initialPage)
          delay(250L)
        } finally {
          initialScrollHappened = true
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = { onViewPagerInitialized(pagerState) }
    )

    LaunchedEffect(
      key1 = currentPageIndex,
      block = {
        if (!initialScrollHappened) {
          return@LaunchedEffect
        }

        mediaViewerScreenState.onCurrentPagerPageChanged(currentPageIndex)

        val postImageData = images.getOrNull(currentPageIndex)?.postImage
          ?: return@LaunchedEffect

        mediaViewerPostListScroller.onSwipedTo(
          screenKey = openedFromScreen,
          fullImageUrl = postImageData.fullImageAsUrl,
          postDescriptor = postImageData.ownerPostDescriptor
        )
      }
    )

    val coroutineScope = rememberCoroutineScope()

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      count = images.size,
      state = pagerState,
      userScrollEnabled = !isMinimized,
      key = { page -> images[page].fullImageUrlAsString }
    ) { page ->
      val mediaState = remember(key1 = page) {
        val postImage = mediaViewerScreenState.images
          .getOrNull(page)
          ?.postImage
          ?: return@remember null

        return@remember when (postImage.imageType()) {
          ImageType.Static -> MediaState.Static(page)
          ImageType.Video -> MediaState.Video(page)
          ImageType.Unsupported -> MediaState.Unsupported(page)
        }
      }

      if (mediaState == null) {
        return@HorizontalPager
      }

      DisposableEffect(
        key1 = Unit,
        effect = {
          mediaViewerScreenState.addCurrentlyLoadedMediaState(page, mediaState)
          onDispose { mediaViewerScreenState.removeCurrentlyLoadedMediaState(page) }
        }
      )

      if (!initialScrollHappened) {
        return@HorizontalPager
      }

      PagerContent(
        isMinimized = isMinimized,
        page = page,
        images = images,
        mediaViewerScreenState = mediaViewerScreenState,
        pagerState = pagerState,
        toolbarHeight = toolbarHeight,
        availableSize = availableSize,
        mpvSettings = mpvSettings,
        mediaState = mediaState,
        checkLibrariesInstalledAndLoaded = { mpvLibsInstalledAndLoaded.value },
        onPreviewLoadingFinished = onPreviewLoadingFinished,
        onMediaTapped = { coroutineScope.launch { mediaViewerScreenState.toggleMediaViewerUiVisibility() } }
      )
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun PagerContent(
    isMinimized: Boolean,
    page: Int,
    images: List<ImageLoadState>,
    mediaViewerScreenState: MediaViewerScreenState,
    pagerState: PagerState,
    toolbarHeight: Dp,
    availableSize: IntSize,
    mpvSettings: MpvSettings,
    mediaState: MediaState,
    checkLibrariesInstalledAndLoaded: () -> Boolean,
    onPreviewLoadingFinished: (IPostImage) -> Unit,
    onMediaTapped: () -> Unit
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
          mediaViewerScreenState.onPageDisposed(postImageDataLoadState)
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
    var isDragGestureAllowedFunc by remember { mutableStateOf(defaultIsDragGestureAllowedFunc) }

    DraggableArea(
      closeScreen = { stopPresenting() },
      isDragGestureAllowedFunc = { currPosition, startPosition ->
        return@DraggableArea !isMinimized && isDragGestureAllowedFunc(currPosition, startPosition)
      }
    ) {
      when (postImageDataLoadState) {
        is ImageLoadState.PreparingForLoading -> {
          var loadingProgressMut by remember { mutableStateOf<Pair<Int, Float>?>(null) }
          var minimumLoadTimePassed by remember { mutableStateOf(false) }

          LaunchedEffect(
            key1 = Unit,
            block = {
              delay(500)
              minimumLoadTimePassed = true
            }
          )

          LoadFullImage(
            mediaViewerScreenState = mediaViewerScreenState,
            postImageDataLoadState = postImageDataLoadState,
            onLoadProgressUpdated = { restartIndex, progress ->
              loadingProgressMut = Pair(restartIndex, progress)
            }
          )

          displayImagePreviewMovable()

          val loadingProgress = loadingProgressMut
          if (loadingProgress == null && minimumLoadTimePassed) {
            KurobaComposeLoadingIndicator()
          } else if (loadingProgress != null) {
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

          when (mediaState) {
            is MediaState.Static -> {
              if (isMinimized) {
                displayImagePreviewMovable()
              } else {
                val imageFile = checkNotNull(postImageDataLoadState.imageFile) { "Can't stream static images" }

                DisplayFullImage(
                  availableSize = availableSize,
                  postImageDataLoadState = postImageDataLoadState,
                  imageFile = imageFile,
                  setIsDragGestureAllowedFunc = { func -> isDragGestureAllowedFunc = func },
                  onFullImageLoaded = { fullMediaLoaded = true },
                  onFullImageFailedToLoad = { fullMediaLoaded = false },
                  onImageTapped = { onMediaTapped() },
                  reloadImage = {
                    coroutineScope.launch {
                      mediaViewerScreenViewModel.removeFileFromDisk(postImageDataLoadState.postImage)
                      mediaViewerScreenState.reloadImage(page, postImageDataLoadState)
                    }
                  }
                )
              }
            }
            is MediaState.Video -> {
              val muteByDefault by mediaViewerScreenState.muteByDefault

              LaunchedEffect(
                key1 = muteByDefault,
                block = { mediaState.isMutedState.value = muteByDefault }
              )

              LaunchedEffect(
                key1 = Unit,
                block = { isDragGestureAllowedFunc = defaultIsDragGestureAllowedFunc }
              )

              DisplayVideo(
                isMinimized = isMinimized,
                pageIndex = page,
                pagerState = pagerState,
                toolbarHeight = toolbarHeight,
                mpvSettings = mpvSettings,
                postImageDataLoadState = postImageDataLoadState,
                snackbarManager = snackbarManager,
                checkLibrariesInstalledAndLoaded = checkLibrariesInstalledAndLoaded,
                onPlayerLoaded = { fullMediaLoaded = true },
                onPlayerUnloaded = { fullMediaLoaded = false },
                videoMediaState = mediaState,
                onVideoTapped = { onMediaTapped() },
                installMpvLibsFromGithubButtonClicked = {
                  onInstallMpvLibsFromGithubButtonClicked(
                    mpvSettings = mpvSettings,
                    context = context
                  )
                },
                toggleMuteByDefaultState = { mediaViewerScreenState.toggleMuteByDefault() }
              )
            }
            is MediaState.Unsupported -> {
              LaunchedEffect(
                key1 = Unit,
                block = { isDragGestureAllowedFunc = defaultIsDragGestureAllowedFunc }
              )

              DisplayUnsupportedMedia(
                isMinimized = isMinimized,
                toolbarHeight = toolbarHeight,
                postImageDataLoadState = postImageDataLoadState,
                onFullImageLoaded = { fullMediaLoaded = true },
                onFullImageFailedToLoad = { fullMediaLoaded = false },
                onImageTapped = { onMediaTapped() }
              )
            }
          }
        }
        is ImageLoadState.Error -> {
          DisplayImageLoadError(
            isMinimized = isMinimized,
            toolbarHeight = toolbarHeight,
            postImageDataLoadState = postImageDataLoadState
          )
        }
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
              onClick = { appRestarter.restart() }
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

    val index = mediaViewerScreenState.imagesMutable().indexOfFirst { imageLoadState ->
      imageLoadState.fullImageUrl == postImageData.fullImageAsUrl
    }

    // We can just stream videos without having to load them first
    if (postImageDataLoadState.postImage.imageType() == ImageType.Video) {
      mediaViewerScreenState.imagesMutable().set(index, ImageLoadState.Ready(postImageData, null))
      return
    }

    if (index < 0) {
      val exception = MediaViewerScreenViewModel.ImageLoadException(
        fullImageUrl,
        appContenxt.getString(R.string.media_viewer_failed_to_find_image_in_images)
      )

      val imageLoadState = ImageLoadState.Error(postImageData, exception)
      mediaViewerScreenState.imagesMutable().set(index, imageLoadState)

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

            mediaViewerScreenState.imagesMutable().set(index, imageLoadState)
          }
          is ImageLoadState.PreparingForLoading,
          is ImageLoadState.Ready -> {
            mediaViewerScreenState.imagesMutable().set(index, imageLoadState)
          }
        }
      }
  }

  @Composable
  private fun DisplayImageLoadError(
    isMinimized: Boolean,
    toolbarHeight: Dp,
    postImageDataLoadState: ImageLoadState.Error
  ) {
    if (isMinimized) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        KurobaComposeIcon(
          modifier = Modifier.size(24.dp),
          drawableId = R.drawable.ic_baseline_warning_24
        )
      }

      return
    }

    val additionalPaddings = remember(toolbarHeight) { PaddingValues(top = toolbarHeight) }

    InsetsAwareBox(
      modifier = Modifier.fillMaxSize(),
      additionalPaddings = additionalPaddings
    ) {
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
        .size(Size.ORIGINAL)
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

                logcat(TAG, LogPriority.VERBOSE) { "onPreviewLoadingFinished() url=\'${postImageData.fullImageAsString}\'" }
                onPreviewLoadingFinished(postImageData)
              }
            }
          }
        )
      }
    )
  }

  companion object {
    private const val TAG = "MediaViewerScreen"
    val SCREEN_KEY = ScreenKey("MediaViewerScreen")
  }
}