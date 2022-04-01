package com.github.k1rakishou.kurobaexlite.ui.screens.media

import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.progress.ProgressScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.DisplayFullImage
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.DisplayUnsupportedMedia
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.DisplayVideo
import java.io.IOException
import java.util.Locale
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
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY

  class MediaViewerScreenState {
    var images: SnapshotStateList<ImageLoadState>? = null
    val initialPage = mutableStateOf<Int?>(null)

    fun isLoaded(): Boolean = initialPage.value != null && images != null
    fun requireImages(): SnapshotStateList<ImageLoadState> = requireNotNull(images) { "images not initialized yet!" }
  }

  @Composable
  override fun CardContent() {
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

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
      var currentImageIndex by remember { mutableStateOf(0) }
      var targetImageIndex by remember { mutableStateOf(0) }
      var offset by remember { mutableStateOf(0f) }

      MediaViewerPager(
        toolbarHeight = toolbarHeight,
        mediaViewerScreenState = mediaViewerScreenState,
        onPageChanged = { currentPage, targetPage, pageOffset ->
          currentImageIndex = currentPage
          targetImageIndex = targetPage
          offset = pageOffset
        }
      )

      MediaViewerToolbar(
        toolbarHeight = toolbarHeight,
        mediaViewerScreenState = mediaViewerScreenState,
        currentImageIndex = currentImageIndex,
        targetImageIndex = targetImageIndex,
        offset = offset
      )
    }
  }

  @Composable
  private fun MediaViewerToolbar(
    toolbarHeight: Dp,
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
        toolbarHeight = toolbarHeight,
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
    currentImageData: IPostImage
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
    toolbarHeight: Dp,
    mediaViewerScreenState: MediaViewerScreenState,
    onPageChanged: (Int, Int, Float) -> Unit
  ) {
    if (!mediaViewerScreenState.isLoaded()) {
      return
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
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
            onLoadProgressUpdated = { restartIndex, progress -> loadingProgressMut = Pair(restartIndex, progress) }
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
                showVideoControls = { /*TODO*/ },
                hideVideoControls = { /*TODO*/ },
                onVideoTapped = { coroutineScope.launch { onBackPressed() } },
                installMpvLibsFromGithubButtonClicked = { onInstallMpvLibsFromGithubButtonClicked(mpvSettings, context) }
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
  private fun DisplayLoadingProgressIndicator(restartIndex: Int, progress: Float) {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current

    val arcSize = with(density) { remember { 42.dp.toPx() } }
    val width = with(density) { remember { 4.dp.toPx() } }
    val textSize = with(density) { 14.dp.toPx() }

    val style = remember { Stroke(width = width) }
    val rotationAnimateable = remember { Animatable(0f) }
    val animationSpec = remember {
      infiniteRepeatable<Float>(animation = tween(durationMillis = 2500), repeatMode = RepeatMode.Restart)
    }
    val textPaint = remember {
      TextPaint().apply {
        this.textSize = textSize
        this.color = android.graphics.Color.WHITE
        this.style = Paint.Style.FILL
        this.setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
      }
    }

    val maxRestarts = MediaViewerScreenViewModel.MAX_RETRIES

    LaunchedEffect(
      key1 = Unit,
      block = { rotationAnimateable.animateTo(targetValue = 1f, animationSpec = animationSpec) }
    )

    val rotation by rotationAnimateable.asState()
    val text = "${restartIndex}/$maxRestarts"

    val textSizeMeasured = remember(key1 = text) {
      val rect = Rect()
      textPaint.getTextBounds(text, 0, text.length, rect)

      return@remember Size(
        rect.width().toFloat(),
        rect.height().toFloat()
      )
    }

    Canvas(
      modifier = Modifier.fillMaxSize(),
      onDraw = {
        val center = this.size.center
        val topLeft = Offset(center.x - (arcSize / 2f), center.y - (arcSize / 2f))
        val size = Size(arcSize, arcSize)

        rotate(degrees = rotation * 360f, pivot = center) {
          drawArc(
            color = chanTheme.accentColorCompose,
            startAngle = 0f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = size,
            style = style
          )
        }

        if (restartIndex > 0) {
          drawContext.canvas.nativeCanvas.drawText(
            text,
            center.x - (textSizeMeasured.width / 2f),
            center.y + (textSizeMeasured.height / 2f),
            textPaint
          )
        }
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
                  logcat { "Got NeedRestart state, waiting ${currentRetryIndex} seconds and then restarting image load" }
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