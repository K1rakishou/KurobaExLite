package com.github.k1rakishou.kurobaexlite.features.media

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withTranslation
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen.Companion.defaultIsDragGestureAllowedFunc
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
import com.github.k1rakishou.kurobaexlite.features.posts.reply.MediaViewerPopupPostsScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupPostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.rememberPostListSelectionState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppRestarter
import com.github.k1rakishou.kurobaexlite.helpers.RuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.model.data.originalFileNameWithExtension
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaBottomSheet
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaBottomSheetState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalRuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.GenericLazyStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyListStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.passClicksThrough
import com.github.k1rakishou.kurobaexlite.ui.helpers.progress.ProgressScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberKurobaBottomSheetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue

class MediaViewerScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val mediaViewerScreenViewModel: MediaViewerScreenViewModel by componentActivity.viewModel()
  private val popupPostsScreenViewModel: MediaViewerPopupPostsScreenViewModel by componentActivity.viewModel()
  private val appRestarter: AppRestarter by inject(AppRestarter::class.java)

  private val linkableClickHelper by lazy {
    return@lazy LinkableClickHelper(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      screenCoroutineScope = screenCoroutineScope,
      appResources = appResources
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY

  override val unpresentAnimation: NavigationRouter.ScreenAnimation
    get() = NavigationRouter.ScreenAnimation.Pop(screenKey)

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      mediaViewerScreenViewModel.onScreenDisposed()
      popupPostsScreenViewModel.clearPostReplyChainStack(screenKey)
    }

    super.onDisposed(screenDisposeEvent)
  }

  @Composable
  override fun BackgroundContent() {
    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      CardContent()
    }
  }

  @Composable
  override fun DefaultFloatingScreenBackPressHandler() {
    // Disable default back press handler, we have out custom.
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun CardContent() {
    val mediaViewerParamsMut by listenForArgumentsNullable<MediaViewerParams?>(mediaViewerParamsKey, null).collectAsState()
    val openedFromScreenMut by listenForArgumentsNullable<ScreenKey?>(openedFromScreenKey, null).collectAsState()

    val mediaViewerParams = mediaViewerParamsMut ?: return
    val openedFromScreen = openedFromScreenMut ?: return

    val context = LocalContext.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val runtimePermissionsHelper = LocalRuntimePermissionsHelper.current
    val mediaViewerScreenState = mediaViewerScreenViewModel.mediaViewerScreenState

    val coroutineScope = rememberCoroutineScope()
    val kurobaBottomSheetState = rememberKurobaBottomSheetState()

    LaunchedEffect(
      key1 = Unit,
      block = { softwareKeyboardController?.hide() }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        mediaViewerScreenState.snackbarFlow.collect { message ->
          if (!navigationRouter.isTopmostScreen(this@MediaViewerScreen)) {
            return@collect
          }

          // Hardcode for now, we only have 1 snackbar emitted from the mediaViewerScreenState
          snackbarManager.toast(
            message = message,
            screenKey = SCREEN_KEY,
            toastId = NEW_MEDIA_VIEWER_IMAGES_ADDED_TOAST_ID
          )
        }
      }
    )

    HandleBackPresses {
      if (popupPostsScreenViewModel.popReplyChain(screenKey)) {
        return@HandleBackPresses true
      }

      // We need MonotonicFrameClock to execute an animation
      if (withContext(coroutineScope.coroutineContext) { kurobaBottomSheetState.onBackPressed() }) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses stopPresenting()
    }

    MediaViewerContent(
      screenKey = screenKey,
      openedFromScreen = openedFromScreen,
      mediaViewerParams = mediaViewerParams,
      mediaViewerScreenState = mediaViewerScreenState,
      kurobaBottomSheetState = kurobaBottomSheetState,
      linkableClickHelperProvider = { linkableClickHelper },
      onDownloadButtonClicked = { postImage ->
        onDownloadButtonClicked(
          context = context,
          runtimePermissionsHelper = runtimePermissionsHelper,
          postImage = postImage
        )
      },
      onInstallMpvLibsFromGithubButtonClicked = {
        onInstallMpvLibsFromGithubButtonClicked(
          mpvSettings = mediaViewerScreenViewModel.mpvSettings,
          context = context.applicationContext
        )
      },
      loadFullImage = { retriesCount, postImageDataLoadState, onLoadProgressUpdated ->
        coroutineScope.launch {
          loadFullImageInternal(
            appContenxt = context.applicationContext,
            coroutineScope = coroutineScope,
            retriesCount = retriesCount,
            postImageDataLoadState = postImageDataLoadState,
            mediaViewerScreenState = mediaViewerScreenState,
            onLoadProgressUpdated = onLoadProgressUpdated
          )
        }
      },
      onOpeningCatalogOrThread = {
        stopPresenting()
        ScreenCallbackStorage.invokeCallback(screenKey, openingCatalogOrScreenCallbackKey)
      },
      onToolbarBackPressed = { stopPresenting() },
      stopPresenting = { stopPresenting() },
    )
  }

  private fun onInstallMpvLibsFromGithubButtonClicked(
    mpvSettings: MpvSettings,
    context: Context
  ) {
    var job: Job? = null

    val progressScreen = ComposeScreen.createScreen<ProgressScreen>(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      args = {
        putString(ProgressScreen.TITLE, context.resources.getString(R.string.media_viewer_plugins_loading_libs))
        putBoolean(ProgressScreen.CANCELLABLE, true)
      },
      callbacks = {
        callback(
          callbackKey = ProgressScreen.CANCELLATION_CALLBACK,
          func = {
            job?.cancel()
            job = null
          }
        )
      }
    )

    job = mediaViewerScreenViewModel.installMpvLibsFromGithub(
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
                error.errorMessageOrClassName(userReadable = true)
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

    val index = mediaViewerScreenState.mediaListMutable().indexOfFirst { imageLoadState ->
      imageLoadState.uniqueKey() == postImageData.uniqueKey()
    }

    // We can just stream videos without having to load them first
    if (postImageDataLoadState.postImage.imageType() == ImageType.Video) {
      mediaViewerScreenState.mediaListMutable().set(index, ImageLoadState.Ready(postImageData, null, null))
      return
    }

    if (index < 0) {
      val exception = MediaViewerScreenViewModel.ImageLoadException(
        fullImageUrl,
        appContenxt.getString(R.string.media_viewer_failed_to_find_image_in_images)
      )

      val imageLoadState = ImageLoadState.Error(postImageData, exception)
      mediaViewerScreenState.mediaListMutable().set(index, imageLoadState)

      return
    }

    val fullImageUniqueKey = postImageDataLoadState.uniqueKey()

    if (!mediaViewerScreenViewModel.enqueueMediaLoadRequest(fullImageUniqueKey)) {
      // Already enqueued
      return
    }

    try {
      loadFullImageAndListenForProgress(
        index = index,
        retriesCount = retriesCount,
        appContext = appContenxt,
        coroutineScope = coroutineScope,
        postImageDataLoadState = postImageDataLoadState,
        onLoadProgressUpdated = onLoadProgressUpdated,
        mediaViewerScreenState = mediaViewerScreenState
      )
    } finally {
      mediaViewerScreenViewModel.removeEnqueuedMediaLoadRequest(fullImageUniqueKey)
    }
  }

  private suspend fun loadFullImageAndListenForProgress(
    index: Int,
    retriesCount: AtomicInteger,
    appContext: Context,
    coroutineScope: CoroutineScope,
    postImageDataLoadState: ImageLoadState.PreparingForLoading,
    onLoadProgressUpdated: (Int, Float) -> Unit,
    mediaViewerScreenState: MediaViewerScreenState
  ) {
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
                  logcat {
                    "Got retriable Error state, waiting ${currentRetryIndex} seconds " +
                      "and then restarting image load"
                  }

                  delay(currentRetryIndex * 1000L)

                  loadFullImageAndListenForProgress(
                    index = index,
                    retriesCount = retriesCount,
                    appContext = appContext,
                    coroutineScope = coroutineScope,
                    postImageDataLoadState = postImageDataLoadState,
                    mediaViewerScreenState = mediaViewerScreenState,
                    onLoadProgressUpdated = onLoadProgressUpdated
                  )
                }

                return@collect
              }

              // fallthrough
            }

            mediaViewerScreenState.mediaListMutable().set(index, imageLoadState)
          }
          is ImageLoadState.PreparingForLoading,
          is ImageLoadState.Ready -> {
            mediaViewerScreenState.mediaListMutable().set(index, imageLoadState)
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
            error.errorMessageOrClassName(userReadable = true)
          )

          snackbarManager.toast(message = message, screenKey = MainScreen.SCREEN_KEY)
        }
      }
    )
  }

  companion object {
    internal const val TAG = "MediaViewerScreen"

    const val mediaViewerParamsKey = "media_viewer_params"
    const val openedFromScreenKey = "opened_from_screen"

    const val openingCatalogOrScreenCallbackKey = "on_opening_catalog_or_screen"

    val SCREEN_KEY = ScreenKey("MediaViewerScreen")

    private const val NEW_MEDIA_VIEWER_IMAGES_ADDED_TOAST_ID = "new_media_viewer_images_added_toast_id"

    internal val defaultIsDragGestureAllowedFunc: (currPosition: Offset, startPosition: Offset) -> Boolean = { _, _ -> true }
  }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewerContent(
  screenKey: ScreenKey,
  openedFromScreen: ScreenKey,
  mediaViewerParams: MediaViewerParams,
  mediaViewerScreenState: MediaViewerScreenState,
  kurobaBottomSheetState: KurobaBottomSheetState,
  linkableClickHelperProvider: () -> LinkableClickHelper,
  onDownloadButtonClicked: (IPostImage) -> Unit,
  onInstallMpvLibsFromGithubButtonClicked: () -> Unit,
  loadFullImage: (AtomicInteger, ImageLoadState.PreparingForLoading, (Int, Float) -> Unit) -> Unit,
  onOpeningCatalogOrThread: () -> Unit,
  onToolbarBackPressed: () -> Unit,
  stopPresenting: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val bgColor = Color.Black
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage = koinRemember()

  var clickedThumbnailBounds by remember { mutableStateOf(clickedThumbnailBoundsStorage.getBounds()) }
  var transitionFinished by remember { mutableStateOf(false) }
  var previewLoadingFinished by remember { mutableStateOf(false) }

  val animatable = remember { Animatable(0f) }
  val animationProgress by animatable.asState()

  var availableSizeMut by remember(key1 = Unit) { mutableStateOf<IntSize>(IntSize.Zero) }
  val availableSize = availableSizeMut

  var pagerStateHolder by remember { mutableStateOf<PagerState?>(null) }
  val coroutineScope = rememberCoroutineScope()

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
        KurobaBottomSheet(
          modifier = Modifier.fillMaxSize(),
          sheetPeekHeight = 0.dp,
          kurobaBottomSheetState = kurobaBottomSheetState,
          sheetBackgroundColor = chanTheme.backColor,
          sheetContent = { sheetPaddingValues ->
            MediaViewerBottomSheet(
              chanDescriptor = mediaViewerParams.chanDescriptor,
              screenKey = screenKey,
              kurobaBottomSheetState = kurobaBottomSheetState,
              mediaViewerScreenState = mediaViewerScreenState,
              sheetPaddingValues = sheetPaddingValues,
              linkableClickHelperProvider = linkableClickHelperProvider,
              scrollToImagesByIndex = { imageToScrollToIndex ->
                pagerStateHolder?.let { pagerState ->
                  if (pagerState.currentPage == imageToScrollToIndex) {
                    return@let
                  }

                  coroutineScope.launch { pagerState.scrollToPage(imageToScrollToIndex) }
                }
              },
              onOpeningCatalogOrThread = {
                coroutineScope.launch { kurobaBottomSheetState.collapse() }
                onOpeningCatalogOrThread()
              }
            )
          },
          content = {
            MediaViewerContentAfterTransition(
              pagerState = pagerStateHolder,
              availableSize = availableSize,
              toolbarHeight = toolbarHeight,
              screenKey = screenKey,
              openedFromScreen = openedFromScreen,
              onViewPagerInitialized = { pagerState -> pagerStateHolder = pagerState },
              onDownloadButtonClicked = onDownloadButtonClicked,
              onShowPostWithCommentsClicked = {
                if (kurobaBottomSheetState.isCollapsedOrCollapsing) {
                  coroutineScope.launch { kurobaBottomSheetState.open() }
                }
              },
              onToolbarBackPressed = onToolbarBackPressed,
              onInstallMpvLibsFromGithubButtonClicked = onInstallMpvLibsFromGithubButtonClicked,
              loadFullImage = loadFullImage,
              stopPresenting = stopPresenting,
              mediaViewerScreenState = mediaViewerScreenState,
              onPreviewLoadingFinished = { postImage ->
                if (previewLoadingFinished) {
                  return@MediaViewerContentAfterTransition
                }

                val bounds = clickedThumbnailBounds
                val previewLoadingFinishedForOutThumbnail = bounds == null ||
                  postImage.fullImageAsString == bounds.postImage.fullImageAsString

                logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) {
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
        )
      }
    }

    TransitionPreview(
      animatable = animatable,
      clickedThumbnailBounds = clickedThumbnailBounds,
      onTransitionFinished = {
        if (!transitionFinished) {
          logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) {
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

@Composable
private fun MediaViewerBottomSheet(
  chanDescriptor: ChanDescriptor,
  screenKey: ScreenKey,
  kurobaBottomSheetState: KurobaBottomSheetState,
  mediaViewerScreenState: MediaViewerScreenState,
  sheetPaddingValues: PaddingValues,
  linkableClickHelperProvider: () -> LinkableClickHelper,
  scrollToImagesByIndex: (Int) -> Unit,
  onOpeningCatalogOrThread: () -> Unit
) {
  val insets = LocalWindowInsets.current
  val context = LocalContext.current
  val orientation = LocalConfiguration.current.orientation
  val coroutineScope = rememberCoroutineScope()

  val popupPostsScreenViewModel: MediaViewerPopupPostsScreenViewModel = koinRememberViewModel()
  val catalogScreenViewModel: CatalogScreenViewModel = koinRememberViewModel()
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val androidHelpers: AndroidHelpers = koinRemember()

  val currentPageIndex by mediaViewerScreenState.currentPageIndex
  var postsLoadedOnce by remember(key1 = currentPageIndex) { mutableStateOf(false) }
  val postImage = currentPageIndex?.let { pageIndex -> mediaViewerScreenState.mediaList.getOrNull(pageIndex)?.postImage }
  val sheetPaddingValuesUpdated by rememberUpdatedState(newValue = sheetPaddingValues)

  val postListOptions by remember {
    derivedStateOf {
      val paddingValues = PaddingValues(
        bottom = insets.bottom + sheetPaddingValuesUpdated.calculateBottomPadding()
      )

      PostListOptions(
        isCatalogMode = chanDescriptor is CatalogDescriptor,
        showThreadStatusCell = false,
        textSelectionEnabled = false,
        isInPopup = true,
        openedFromScreenKey = screenKey,
        pullToRefreshEnabled = false,
        contentPadding = paddingValues,
        mainUiLayoutMode = MainUiLayoutMode.Phone,
        detectLinkableClicks = true,
        orientation = orientation,
        postViewMode = PostViewMode.List
      )
    }
  }

  val _lazyListState = rememberLazyListState()
  val lazyListStateWrapper = remember(key1 = _lazyListState) { LazyListStateWrapper(_lazyListState) }
  val postListSelectionState = rememberPostListSelectionState(postSelectionEnabled = false)

  Column(modifier = Modifier.fillMaxSize()) {
    PostListContent(
      lazyStateWrapper = lazyListStateWrapper as GenericLazyStateWrapper,
      postListOptions = postListOptions,
      postListSelectionState = postListSelectionState,
      postsScreenViewModelProvider = { popupPostsScreenViewModel },
      onPostCellClicked = { postCellData -> /*no-op*/ },
      onPostCellLongClicked = { postCellData ->
        // no-op
      },
      onLinkableClicked = { postCellData, linkable ->
        linkableClickHelperProvider().processClickedLinkable(
          context = context,
          sourceScreenKey = screenKey,
          postCellData = postCellData,
          linkable = linkable,
          loadThreadFunc = { threadDescriptor ->
            threadScreenViewModel.loadThread(threadDescriptor)
            onOpeningCatalogOrThread()
          },
          loadCatalogFunc = { catalogDescriptor ->
            catalogScreenViewModel.loadCatalog(catalogDescriptor)
            onOpeningCatalogOrThread()
          },
          showRepliesForPostFunc = { postViewMode ->
            coroutineScope.launch {
              popupPostsScreenViewModel.loadRepliesForMode(screenKey, postViewMode)
            }
          },
        )
      },
      onLinkableLongClicked = { postCellData, linkable ->
        linkableClickHelperProvider().processLongClickedLinkable(
          sourceScreenKey = screenKey,
          postCellData = postCellData,
          linkable = linkable
        )
      },
      onPostRepliesClicked = { chanDescriptor, postDescriptor ->
        coroutineScope.launch {
          val popupPostViewMode = PopupPostsScreen.PopupPostViewMode.RepliesFrom(
            chanDescriptor = chanDescriptor,
            postDescriptor = postDescriptor
          )

          popupPostsScreenViewModel.loadRepliesForMode(screenKey, popupPostViewMode)
        }
      },
      onCopySelectedText = { selectedText ->
        androidHelpers.copyToClipboard("Selected text", selectedText)
      },
      onQuoteSelectedText = { withText, selectedText, postCellData ->
        // no-op
      },
      onPostImageClicked = { chanDescriptor, clickedPostImage, thumbnailBoundsInRoot ->
        val imageToScrollToIndex = mediaViewerScreenState.mediaList
          .indexOfFirst { it.uniqueKey() == clickedPostImage.uniqueKey() }
          .takeIf { index -> index >= 0 }

        if (imageToScrollToIndex == null) {
          return@PostListContent
        }

        coroutineScope.launch { kurobaBottomSheetState.collapse() }
        scrollToImagesByIndex(imageToScrollToIndex)
      },
      onPostImageLongClicked = { _, _ -> },
      onGoToPostClicked = { postCellData ->
        when (postCellData.chanDescriptor) {
          is CatalogDescriptor -> {
            catalogScreenViewModel.scrollToPost(postDescriptor = postCellData.postDescriptor, blink = true)
          }
          is ThreadDescriptor -> {
            threadScreenViewModel.scrollToPost(postDescriptor = postCellData.postDescriptor, blink = true)
          }
        }

        onOpeningCatalogOrThread()
      },
      onPostListScrolled = { delta -> /*no-op*/ },
      onPostListTouchingTopOrBottomStateChanged = { touching -> /*no-op*/ },
      onCurrentlyTouchingPostList = { touching -> /*no-op*/ },
      onFastScrollerDragStateChanged = { dragging -> /*no-op*/ },
    )
  }

  LaunchedEffect(
    key1 = postImage,
    block = {
      if (postImage == null || postsLoadedOnce) {
        return@LaunchedEffect
      }

      postsLoadedOnce = true

      popupPostsScreenViewModel.clearPostReplyChainStack(screenKey)
      popupPostsScreenViewModel.loadRepliesForModeInitial(
        screenKey = screenKey,
        popupPostViewMode = PopupPostsScreen.PopupPostViewMode.RepliesFrom(
          chanDescriptor = chanDescriptor,
          postDescriptor = postImage.ownerPostDescriptor,
          includeThisPost = true
        )
      )
    }
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewerContentAfterTransition(
  pagerState: PagerState?,
  mediaViewerScreenState: MediaViewerScreenState,
  screenKey: ScreenKey,
  availableSize: IntSize,
  toolbarHeight: Dp,
  openedFromScreen: ScreenKey,
  onViewPagerInitialized: (PagerState) -> Unit,
  onPreviewLoadingFinished: (IPostImage) -> Unit,
  onDownloadButtonClicked: (IPostImage) -> Unit,
  onShowPostWithCommentsClicked: () -> Unit,
  onToolbarBackPressed: () -> Unit,
  onInstallMpvLibsFromGithubButtonClicked: () -> Unit,
  loadFullImage: (AtomicInteger, ImageLoadState.PreparingForLoading, (Int, Float) -> Unit) -> Unit,
  stopPresenting: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val insets = LocalWindowInsets.current

  val coroutineScope = rememberCoroutineScope()
  val mediaViewerScreenViewModel: MediaViewerScreenViewModel = koinRememberViewModel()

  val toolbarTotalHeight = remember { mutableStateOf<Int?>(null) }
  val globalMediaViewerControlsVisible by remember { mutableStateOf(true) }
  val mediaList = mediaViewerScreenState.mediaList
  val backgroundColor = chanTheme.backColor

  MediaViewerPagerContainer(
    availableSize = availableSize,
    toolbarHeight = toolbarHeight,
    openedFromScreen = openedFromScreen,
    mediaViewerScreenState = mediaViewerScreenState,
    onViewPagerInitialized = onViewPagerInitialized,
    onPreviewLoadingFinished = onPreviewLoadingFinished,
    onInstallMpvLibsFromGithubButtonClicked = onInstallMpvLibsFromGithubButtonClicked,
    loadFullImage = loadFullImage,
    stopPresenting = stopPresenting,
  )

  if (pagerState == null || mediaList.isEmpty()) {
    return
  }

  val mediaViewerUiVisible by mediaViewerScreenState.mediaViewerUiVisible
  val mediaViewerUiAlpha by animateFloatAsState(
    targetValue = if (mediaViewerUiVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 300)
  )

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier.Companion
        .align(Alignment.TopCenter)
        .graphicsLayer { alpha = mediaViewerUiAlpha }
        .passClicksThrough(passClicks = !mediaViewerUiVisible)
        .onSizeChanged { size -> toolbarTotalHeight.value = size.height }
        .drawBehind { drawRect(backgroundColor) }
    ) {
      MediaViewerToolbar(
        toolbarHeight = toolbarHeight,
        backgroundColor = backgroundColor,
        screenKey = screenKey,
        mediaViewerScreenState = mediaViewerScreenState,
        pagerState = pagerState,
        onDownloadMediaClicked = { postImage -> onDownloadButtonClicked(postImage) },
        onBackPressed = { onToolbarBackPressed() }
      )
    }

    val currentPageIndex by remember { derivedStateOf { pagerState.currentPage } }
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

    Column(
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      if (currentLoadedMedia?.supportedMedia == true) {
        MediaViewerBottomIcons(
          mediaViewerScreenState = mediaViewerScreenState,
          currentPageIndex = currentPageIndex,
          mediaViewerUiAlpha = mediaViewerUiAlpha,
          backgroundColor = backgroundColor,
          mediaViewerUiVisible = mediaViewerUiVisible,
          onShowPostWithCommentsClicked = onShowPostWithCommentsClicked
        )

        Spacer(modifier = Modifier.height(24.dp))
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .passClicksThrough(passClicks = !mediaViewerUiVisible)
          .graphicsLayer { this.alpha = alphaAnimated }
          .drawBehind { drawRect(backgroundColor) }
      ) {
        if (currentLoadedMedia is MediaState.Video) {
          MediaViewerScreenVideoControls(currentLoadedMedia)
        }

        Spacer(modifier = Modifier.height(insets.bottom))
      }
    }

    if (mediaList.isNotNullNorEmpty()) {
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
            .alpha(mediaViewerUiAlpha)
            .passClicksThrough(passClicks = !mediaViewerUiVisible)
        ) {
          MediaViewerPreviewStrip(
            pagerState = pagerState,
            mediaList = mediaList,
            mediaViewerScreenState = mediaViewerScreenState,
            backgroundColor = backgroundColor,
            toolbarHeightPx = actualToolbarCalculatedHeight,
            onPreviewClicked = { postImage ->
              coroutineScope.launch {
                mediaList
                  .indexOfFirst { it.uniqueKey() == postImage.uniqueKey() }
                  .takeIf { index -> index >= 0 }
                  ?.let { scrollIndex -> pagerState.scrollToPage(scrollIndex) }
              }
            }
          )
        }
      }
    }
  }
}

@Composable
private fun MediaViewerBottomIcons(
  mediaViewerScreenState: MediaViewerScreenState,
  currentPageIndex: Int,
  mediaViewerUiAlpha: Float,
  backgroundColor: Color,
  mediaViewerUiVisible: Boolean,
  onShowPostWithCommentsClicked: () -> Unit
) {
  val mediaViewerScreenViewModel: MediaViewerScreenViewModel = koinRememberViewModel()
  val mediaList = mediaViewerScreenState.mediaList
  val chanDescriptor = mediaViewerScreenState.chanDescriptor

  if (chanDescriptor !is ThreadDescriptor) {
    return
  }

  Row {
    Spacer(modifier = Modifier.weight(1f))

    Box {
      KurobaComposeClickableIcon(
        modifier = Modifier
          .graphicsLayer { alpha = mediaViewerUiAlpha }
          .size(42.dp)
          .background(backgroundColor, CircleShape)
          .padding(8.dp),
        drawableId = R.drawable.ic_baseline_comment_24,
        enabled = mediaViewerUiVisible,
        onClick = { onShowPostWithCommentsClicked() }
      )

      val currentPostImage = mediaList.getOrNull(currentPageIndex)?.postImage

      val replyCountToCurrentImagePostMut by produceState<String?>(
        initialValue = null,
        key1 = currentPostImage,
        producer = {
          if (currentPostImage == null) {
            value = null
            return@produceState
          }

          val replyCount = mediaViewerScreenViewModel.getReplyCountToPost(currentPostImage.ownerPostDescriptor)
          if (replyCount <= 0) {
            value = null
            return@produceState
          }

          val replyCountText = if (replyCount > 999) {
            "999+"
          } else {
            replyCount.toString()
          }

          value = replyCountText
        }
      )
      val replyCountToCurrentImagePost = replyCountToCurrentImagePostMut

      if (replyCountToCurrentImagePost != null) {
        val replyCountIconBg = remember { Color.Black.copy(alpha = 0.8f) }

        KurobaComposeText(
          modifier = Modifier
            .graphicsLayer { alpha = mediaViewerUiAlpha }
            .wrapContentSize()
            .background(replyCountIconBg, RoundedCornerShape(25f))
            .align(Alignment.TopEnd)
            .padding(horizontal = 8.dp, vertical = 2.dp),
          text = replyCountToCurrentImagePost,
          color = Color.White,
          fontSize = 12.sp
        )
      }
    }

    Spacer(modifier = Modifier.width(24.dp))
  }
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
        logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) {
          "loadThumbnailBitmap() clickedThumbnailBounds == null"
        }

        onTransitionFinished()
      }
    )

    return
  }

  val bitmapMatrix = remember { Matrix() }
  val postImage = clickedThumbnailBounds.postImage
  val srcBounds = clickedThumbnailBounds.bounds

  val mediaViewerScreenViewModel: MediaViewerScreenViewModel = koinRememberViewModel()

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
      try {
        logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) {
          "TransitionPreview.loadThumbnailBitmap(url=${postImage.thumbnailAsUrl}) start"
        }

        bitmapMut = withTimeout(timeMillis = 1000) { mediaViewerScreenViewModel.loadThumbnailBitmap(context, postImage) }
        if (bitmapMut == null) {
          logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) {
            "TransitionPreview.loadThumbnailBitmap(url=${postImage.thumbnailAsUrl}) canceled"
          }

          onTransitionFinished()
          return@LaunchedEffect
        }

        logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) {
          "TransitionPreview.loadThumbnailBitmap(url=${postImage.thumbnailAsUrl}) success"
        }
      } catch (error: Throwable) {
        logcatError(MediaViewerScreen.TAG) {
          "TransitionPreview.loadThumbnailBitmap(url=${postImage.thumbnailAsUrl}) " +
            "error: ${error.errorMessageOrClassName()}"
        }

        onTransitionFinished()
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
        logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) { "loadThumbnailBitmap() success: true" }
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
  val mediaViewerScreenViewModel: MediaViewerScreenViewModel = koinRememberViewModel()
  val appSettings: AppSettings = koinRemember()

  LaunchedEffect(
    key1 = mediaViewerScreenState,
    key2 = mediaViewerParams,
    block = {
      val initResult = when (mediaViewerParams) {
        is MediaViewerParams.Catalog,
        is MediaViewerParams.Thread -> {
          mediaViewerScreenViewModel.initFromCatalogOrThreadDescriptor(
            chanDescriptor = mediaViewerParams.chanDescriptor,
            initialImageUrl = mediaViewerParams.initialImageUrl
          )
        }
        is MediaViewerParams.Images -> {
          mediaViewerScreenViewModel.initFromImageList(
            chanDescriptor = mediaViewerParams.chanDescriptor,
            images = mediaViewerParams.images,
            initialImageUrl = mediaViewerParams.initialImageUrl
          )
        }
      }

      mediaViewerScreenState.init(
        chanDescriptor = mediaViewerParams.chanDescriptor,
        images = initResult.images,
        pageIndex = initResult.initialPage,
        sourceType = initResult.sourceType,
        mediaViewerUiVisible = appSettings.mediaViewerUiVisible.read()
      )
    }
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewerPagerContainer(
  availableSize: IntSize,
  toolbarHeight: Dp,
  openedFromScreen: ScreenKey,
  mediaViewerScreenState: MediaViewerScreenState,
  onViewPagerInitialized: (PagerState) -> Unit,
  onPreviewLoadingFinished: (IPostImage) -> Unit,
  onInstallMpvLibsFromGithubButtonClicked: () -> Unit,
  loadFullImage: (AtomicInteger, ImageLoadState.PreparingForLoading, (Int, Float) -> Unit) -> Unit,
  stopPresenting: () -> Unit
) {
  val context = LocalContext.current
  val currentPageIndexForInitialScrollMut by mediaViewerScreenState.currentPageIndex
  val mediaListMut = mediaViewerScreenState.mediaList

  val mediaViewerScreenViewModel: MediaViewerScreenViewModel = koinRememberViewModel()
  val mediaViewerPostListScroller: MediaViewerPostListScroller = koinRemember()

  val currentPageIndexForInitialScroll = currentPageIndexForInitialScrollMut
  val mediaList = mediaListMut

  if (currentPageIndexForInitialScroll == null) {
    return
  }

  if (mediaList.isEmpty()) {
    val additionalPaddings = remember(toolbarHeight) { PaddingValues(top = toolbarHeight) }

    InsetsAwareBox(
      modifier = Modifier.fillMaxSize(),
      additionalPaddings = additionalPaddings
    ) {
      KurobaComposeText(text = stringResource(id = R.string.media_viewer_no_images_to_show))
    }

    return
  }

  val mpvSettingsProvider = remember { { mediaViewerScreenViewModel.mpvSettings } }

  val mpvLibsInstalledAndLoaded = remember {
    lazy {
      val librariesInstalled = MPVLib.checkLibrariesInstalled(context.applicationContext, mpvSettingsProvider())
      if (!librariesInstalled) {
        return@lazy false
      }

      return@lazy MPVLib.librariesAreLoaded()
    }
  }

  var initialScrollHappened by remember { mutableStateOf(false) }
  val initialScrollHappenedUpdated by rememberUpdatedState(newValue = initialScrollHappened)

  val pagerState = rememberPagerState()
  val currentPageIndex by remember { derivedStateOf { pagerState.currentPage } }
  val isScrollInProgress by remember { derivedStateOf { pagerState.isScrollInProgress } }

  LaunchedEffect(
    key1 = Unit,
    block = {
      mediaViewerScreenState.mediaNavigationEventFlow.collectLatest { mediaNavigationEvent ->
        if (!initialScrollHappenedUpdated) {
          return@collectLatest
        }

        val currentPage = pagerState.currentPage
        val pageCount = mediaList.size

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
      if (pagerState.currentPage == currentPageIndexForInitialScroll) {
        initialScrollHappened = true
        return@LaunchedEffect
      }

      try {
        pagerState.scrollToPage(currentPageIndexForInitialScroll)
        delay(250L)
      } finally {
        initialScrollHappened = true
      }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      mediaViewerScreenState.scrollToMediaFlow
        // We need to wait for some time before the mediaList is applied to the PagerState
        // otherwise the scrolling won't do anything in case the current pages count is less than
        // the scroll index.
        .debounce(128)
        .collectLatest { (longScroll, pageIndex) ->
          val distance = (pagerState.currentPage - pageIndex).absoluteValue

          try {
            if (longScroll || distance > 1) {
              pagerState.scrollToPage(pageIndex)
            } else {
              pagerState.animateScrollToPage(pageIndex)
            }
          } catch (error: CancellationException) {
            // ignore
          }
        }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = { onViewPagerInitialized(pagerState) }
  )

  LaunchedEffect(
    key1 = currentPageIndex,
    key2 = isScrollInProgress,
    block = {
      if (!initialScrollHappened || isScrollInProgress) {
        return@LaunchedEffect
      }

      mediaViewerScreenState.onCurrentPagerPageChanged(currentPageIndex)

      val postImageData = mediaList.getOrNull(currentPageIndex)?.postImage
      if (postImageData == null) {
        return@LaunchedEffect
      }

      mediaViewerPostListScroller.onSwipedTo(
        screenKey = openedFromScreen,
        fullImageUrl = postImageData.fullImageAsUrl,
        postDescriptor = postImageData.ownerPostDescriptor
      )
    }
  )

  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(
    key1 = currentPageIndex,
    block = {
      mediaViewerScreenState.removeCurrentlyLoadedMediaState(currentPageIndex, mediaList.size)
    }
  )

  HorizontalPager(
    modifier = Modifier.fillMaxSize(),
    pageCount = mediaList.size,
    state = pagerState,
    key = { page -> mediaList.getOrNull(page)?.uniqueKey() ?: page }
  ) { page ->
    val mediaState = remember(key1 = page) {
      val prevMediaState = mediaViewerScreenState.getMediaStateByIndex(page)
      if (prevMediaState != null) {
        return@remember prevMediaState
      }

      val postImage = mediaList
        .getOrNull(page)
        ?.postImage
        ?: return@remember null

      val newMediaState = when (postImage.imageType()) {
        ImageType.Static -> MediaState.Static(page)
        ImageType.Video -> {
          val muteByDefault = mediaViewerScreenState.muteByDefault.value
          MediaState.Video(page, muteByDefault)
        }
        ImageType.Unsupported -> MediaState.Unsupported(page)
      }

      mediaViewerScreenState.addCurrentlyLoadedMediaState(page, newMediaState)
      return@remember newMediaState
    }

    if (mediaState == null) {
      return@HorizontalPager
    }

    if (!initialScrollHappened) {
      return@HorizontalPager
    }

    PageContent(
      page = page,
      mediaViewerScreenState = mediaViewerScreenState,
      pagerState = pagerState,
      toolbarHeight = toolbarHeight,
      availableSize = availableSize,
      mpvSettingsProvider = mpvSettingsProvider,
      mediaState = mediaState,
      checkLibrariesInstalledAndLoaded = { mpvLibsInstalledAndLoaded.value },
      onPreviewLoadingFinished = onPreviewLoadingFinished,
      onMediaTapped = { coroutineScope.launch { mediaViewerScreenState.toggleMediaViewerUiVisibility() } },
      onInstallMpvLibsFromGithubButtonClicked = onInstallMpvLibsFromGithubButtonClicked,
      loadFullImage = loadFullImage,
      stopPresenting = stopPresenting,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageContent(
  page: Int,
  mediaViewerScreenState: MediaViewerScreenState,
  pagerState: PagerState,
  toolbarHeight: Dp,
  availableSize: IntSize,
  mediaState: MediaState,
  mpvSettingsProvider: () -> MpvSettings,
  checkLibrariesInstalledAndLoaded: () -> Boolean,
  onPreviewLoadingFinished: (IPostImage) -> Unit,
  onMediaTapped: () -> Unit,
  onInstallMpvLibsFromGithubButtonClicked: () -> Unit,
  loadFullImage: (AtomicInteger, ImageLoadState.PreparingForLoading, (Int, Float) -> Unit) -> Unit,
  stopPresenting: () -> Unit
) {
  val images = mediaViewerScreenState.mediaList
  val postImageDataLoadStateMut = images.getOrNull(page)
    ?: return
  val postImageDataLoadState = postImageDataLoadStateMut

  val mediaViewerScreenViewModel: MediaViewerScreenViewModel = koinRememberViewModel()

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
    availableSize = availableSize,
    closeScreen = { stopPresenting() },
    isDragGestureAllowedFunc = { currPosition, startPosition ->
      isDragGestureAllowedFunc(currPosition, startPosition)
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

        LaunchedEffect(
          key1 = postImageDataLoadState.postImage.fullImageAsUrl,
          block = {
            val retriesCount = AtomicInteger(0)

            loadFullImage(retriesCount, postImageDataLoadState) { restartIndex, progress ->
              loadingProgressMut = Pair(restartIndex, progress)
            }
          }
        )

        displayImagePreviewMovable()

        val loadingProgress = loadingProgressMut
        if (loadingProgress == null && minimumLoadTimePassed) {
          KurobaComposeLoadingIndicator(fadeInTimeMs = 0)
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
            DisplayFullImage(
              availableSize = availableSize,
              postImageDataLoadState = postImageDataLoadState,
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
              pageIndex = page,
              pagerState = pagerState,
              postImageDataLoadState = postImageDataLoadState,
              mpvSettingsProvider = mpvSettingsProvider,
              checkLibrariesInstalledAndLoaded = checkLibrariesInstalledAndLoaded,
              onPlayerLoaded = { fullMediaLoaded = true },
              onPlayerUnloaded = { fullMediaLoaded = false },
              videoMediaState = mediaState,
              onVideoTapped = { onMediaTapped() },
              installMpvLibsFromGithubButtonClicked = onInstallMpvLibsFromGithubButtonClicked,
              toggleMuteByDefaultState = { mediaViewerScreenState.toggleMuteByDefault() }
            )
          }
          is MediaState.Unsupported -> {
            LaunchedEffect(
              key1 = Unit,
              block = { isDragGestureAllowedFunc = defaultIsDragGestureAllowedFunc }
            )

            DisplayUnsupportedMedia(
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
          postImageDataLoadState = postImageDataLoadState
        )
      }
    }
  }
}

@Composable
private fun DisplayImageLoadError(
  postImageDataLoadState: ImageLoadState.Error
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 24.dp)
      .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f)) },
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      KurobaComposeIcon(
        modifier = Modifier.size(80.dp, 80.dp),
        drawableId = R.drawable.ic_baseline_warning_24
      )

      Spacer(modifier = Modifier.height(32.dp))

      val errorText = remember(key1 = postImageDataLoadState) {
        postImageDataLoadState.exception.errorMessageOrClassName(userReadable = true)
      }

      KurobaComposeText(
        text = errorText,
        fontSize = 14.sp,
        color = Color.White
      )
    }
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
    contentDescription = "Media viewer post thumbnail icon",
    contentScale = ContentScale.Fit,
    content = {
      val state = painter.state
      if (state is AsyncImagePainter.State.Error) {
        logcatError(tag = MediaViewerScreen.TAG) {
          "DisplayImagePreview() url=${postImageData.fullImageAsUrl}, " +
            "postDescriptor=${postImageData.ownerPostDescriptor}, " +
            "error=${state.result.throwable}"
        }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f)) },
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KurobaComposeIcon(
              modifier = Modifier.size(80.dp, 80.dp),
              drawableId = R.drawable.ic_baseline_warning_24
            )

            Spacer(modifier = Modifier.height(32.dp))

            val errorText = remember(key1 = state) {
              state.result.throwable.errorMessageOrClassName(userReadable = true)
            }

            KurobaComposeText(
              text = errorText,
              fontSize = 14.sp,
              color = Color.White
            )
          }
        }
      } else {
        SubcomposeAsyncImageContent()
      }

      LaunchedEffect(
        key1 = state,
        block = {
          if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
            if (!callbackCalled) {
              callbackCalled = true

              logcat(MediaViewerScreen.TAG, LogPriority.VERBOSE) {
                "onPreviewLoadingFinished() url=\'${postImageData.fullImageAsString}\'"
              }
              onPreviewLoadingFinished(postImageData)
            }
          }
        }
      )
    }
  )
}