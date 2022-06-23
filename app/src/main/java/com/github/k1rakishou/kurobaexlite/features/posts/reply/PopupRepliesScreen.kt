package com.github.k1rakishou.kurobaexlite.features.posts.reply

import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastSumBy
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class PopupRepliesScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val popupRepliesScreenViewModel: PopupRepliesScreenViewModel by componentActivity.viewModel()
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val replyViewMode: ReplyViewMode by requireArgumentLazy(REPLY_VIEW_MODE)

  private val postListLayoutId = "PopupRepliesScreen_PostListContent"
  private val buttonsLayoutId = "PopupRepliesScreen_Buttons"
  private val buttonsHeight = 50.dp

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  override val screenKey: ScreenKey = SCREEN_KEY

  override val unpresentAnimation: NavigationRouter.ScreenAnimation
    get() = NavigationRouter.ScreenAnimation.Pop(screenKey)

  @OptIn(ExperimentalMaterialApi::class)
  private val swipeableState = SwipeableState(
    initialValue = Anchors.Visible,
    animationSpec = SwipeableDefaults.AnimationSpec,
    confirmStateChange = { true }
  )

  @OptIn(ExperimentalMaterialApi::class)
  @Composable
  override fun CardContent() {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val swipeDistance = remember(key1 = size) {
      if (size.width > 0) size.width.toFloat() else 1f
    }

    val anchors = remember(key1 = swipeDistance) {
      mapOf(
        -(swipeDistance) to Anchors.Back,
        0f to Anchors.Visible,
        swipeDistance to Anchors.Close
      )
    }

    val currentValue = swipeableState.currentValue
    val targetValue = swipeableState.targetValue
    val currentOffset by swipeableState.offset
    val progress = currentOffset / swipeDistance

    LaunchedEffect(
      key1 = currentValue,
      block = {
        when (currentValue) {
          Anchors.Visible -> {
            // no-op
          }
          Anchors.Back -> onBackPressed()
          Anchors.Close -> stopPresenting()
        }
      }
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .onSizeChanged { newSize -> size = newSize }
    ) {
      val animationTargetValue = if (currentOffset.absoluteValue > 0f) 1f else 0f
      val bgContentAlphaAnimation by animateFloatAsState(targetValue = animationTargetValue)

      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = bgContentAlphaAnimation },
        contentAlignment = Alignment.Center
      ) {
        val textId = when (targetValue) {
          Anchors.Visible -> null
          Anchors.Back ->  R.string.back
          Anchors.Close ->  R.string.close
        }

        if (textId != null) {
          Text(
            modifier = Modifier,
            text = stringResource(id = textId),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp
          )
        }
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .swipeable(
            state = swipeableState,
            anchors = anchors,
            orientation = Orientation.Horizontal,
            thresholds = { _, _ -> FractionalThreshold(0.5f) }
          )
          .absoluteOffset { IntOffset(x = currentOffset.toInt(), y = 0) }
          .graphicsLayer { alpha = (1f - progress.absoluteValue) }
      ) {
        super.CardContent()
      }
    }
  }

  @Composable
  override fun FloatingContent() {
    val orientationMut by globalUiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val postCellCommentTextSizeSp by globalUiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by globalUiInfoManager.postCellSubjectTextSizeSp.collectAsState()
    val postsAsyncDataState by popupRepliesScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()

    val postListOptions by remember {
      derivedStateOf {
        PostListOptions(
          isCatalogMode = false,
          isInPopup = true,
          ownerScreenKey = screenKey,
          pullToRefreshEnabled = false,
          contentPadding = PaddingValues(),
          mainUiLayoutMode = MainUiLayoutMode.Phone,
          postCellCommentTextSizeSp = postCellCommentTextSizeSp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
          detectLinkableClicks = true,
          orientation = orientation
        )
      }
    }

    val density = LocalDensity.current
    val buttonsHeightPx = with(density) { remember(key1 = buttonsHeight) { buttonsHeight.toPx().toInt() } }

    if (postsAsyncDataState !is AsyncData.Uninitialized && postsAsyncDataState !is AsyncData.Loading) {
      PopupRepliesScreenContentLayout(
        postListOptions = postListOptions,
        buttonsHeightPx = buttonsHeightPx
      )
    }

    LaunchedEffect(
      key1 = replyViewMode,
      block = { popupRepliesScreenViewModel.loadRepliesForModeInitial(replyViewMode) }
    )
  }

  @Composable
  private fun PopupRepliesScreenContentLayout(
    postListOptions: PostListOptions,
    buttonsHeightPx: Int
  ) {
    Layout(
      content = { PopupRepliesScreenContent(postListOptions) },
      measurePolicy = { measurables, constraints ->
        val placeables = measurables.map { measurable ->
          when (measurable.layoutId) {
            postListLayoutId -> {
              measurable.measure(constraints.offset(vertical = -buttonsHeightPx))
            }
            buttonsLayoutId -> {
              measurable.measure(
                constraints.copy(
                  minHeight = buttonsHeightPx,
                  maxHeight = buttonsHeightPx
                )
              )
            }
            else -> error("Unexpected layoutId: \'${measurable.layoutId}\'")
          }
        }

        val width = placeables.fastMap { it.width }.fastMaxBy { it } ?: 0
        val height = placeables.fastSumBy { it.height }

        layout(width, height) {
          var takenHeight = 0

          placeables.fastForEach {
            it.placeRelative(0, takenHeight)
            takenHeight += it.height
          }
        }
      }
    )
  }

  @Composable
  private fun PopupRepliesScreenContent(
    postListOptions: PostListOptions
  ) {
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(
      modifier = Modifier
        .layoutId(postListLayoutId)
        .animateContentSize()
    ) {
      PostListContent(
        postListOptions = postListOptions,
        postsScreenViewModel = popupRepliesScreenViewModel,
        onPostCellClicked = { postCellData -> /*no-op*/ },
        onPostCellLongClicked = { postCellData ->
          postLongtapContentMenu.showMenu(
            postListOptions = postListOptions,
            postCellData = postCellData,
            reparsePostsFunc = { postDescriptors ->
              popupRepliesScreenViewModel.reparsePostsByDescriptors(postDescriptors)

              // Reparse the threadScreenViewModel posts too
              threadScreenViewModel.reparsePostsByDescriptors(postDescriptors)
            }
          )
        },
        onLinkableClicked = { postCellData, linkable ->
          linkableClickHelper.processClickedLinkable(
            context = context,
            sourceScreenKey = screenKey,
            postCellData = postCellData,
            linkable = linkable,
            loadThreadFunc = { threadDescriptor ->
              threadScreenViewModel.loadThread(threadDescriptor)
              stopPresenting()
            },
            loadCatalogFunc = { catalogDescriptor ->
              catalogScreenViewModel.loadCatalog(catalogDescriptor)
              stopPresenting()
            },
            showRepliesForPostFunc = { replyViewMode ->
              coroutineScope.launch {
                popupRepliesScreenViewModel.loadRepliesForMode(replyViewMode)
              }
            }
          )
        },
        onLinkableLongClicked = { postCellData, linkable ->
          linkableClickHelper.processLongClickedLinkable(
            sourceScreenKey = screenKey,
            postCellData = postCellData,
            linkable = linkable
          )
        },
        onPostRepliesClicked = { postDescriptor ->
          coroutineScope.launch {
            popupRepliesScreenViewModel.loadRepliesForMode(ReplyViewMode.RepliesFrom(postDescriptor))
          }
        },
        onQuotePostClicked = { postCellData -> },
        onQuotePostWithCommentClicked = { postCellData -> },
        onPostImageClicked = { _, postImageDataResult, thumbnailBoundsInRoot ->
          val postImageData = if (postImageDataResult.isFailure) {
            snackbarManager.errorToast(
              message = postImageDataResult.exceptionOrThrow().errorMessageOrClassName(),
              screenKey = screenKey
            )

            return@PostListContent
          } else {
            postImageDataResult.getOrThrow()
          }

          val collectedImages = popupRepliesScreenViewModel.collectCurrentImages()
          if (collectedImages.isEmpty()) {
            return@PostListContent
          }

          clickedThumbnailBoundsStorage.storeBounds(postImageData, thumbnailBoundsInRoot)

          val mediaViewerScreen = MediaViewerScreen(
            mediaViewerParams = MediaViewerParams.Images(
              images = collectedImages,
              initialImageUrl = postImageData.fullImageAsUrl
            ),
            openedFromScreen = screenKey,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter
          )

          navigationRouter.presentScreen(mediaViewerScreen)
        },
        onPostListScrolled = { delta -> /*no-op*/ },
        onPostListTouchingTopOrBottomStateChanged = { touching -> /*no-op*/ },
        onCurrentlyTouchingPostList = { touching -> /*no-op*/ },
        onFastScrollerDragStateChanged = { dragging -> /*no-op*/ },
        loadingContent = { isInPopup -> /*no-op*/ },
      )
    }

    Row(
      modifier = Modifier
        .layoutId(buttonsLayoutId)
        .fillMaxWidth()
        .height(buttonsHeight)
        .padding(vertical = 4.dp)
    ) {
      val textColor = if (ThemeEngine.isDarkColor(chanTheme.backColor)) {
        Color.LightGray
      } else {
        Color.DarkGray
      }

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        modifier = Modifier
          .fillMaxHeight()
          .weight(0.5f),
        text = stringResource(id = R.string.back),
        customTextColor = textColor,
        onClick = { coroutineScope.launch { onBackPressed() } }
      )

      Spacer(modifier = Modifier.width(4.dp))

      KurobaComposeTextBarButton(
        modifier = Modifier
          .fillMaxHeight()
          .weight(0.5f),
        customTextColor = textColor,
        text = stringResource(id = R.string.close),
        onClick = { stopPresenting() }
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @OptIn(ExperimentalMaterialApi::class)
  override suspend fun onFloatingControllerBackPressed(): Boolean {
    if (popupRepliesScreenViewModel.popReplyChain()) {
      swipeableState.snapTo(Anchors.Visible)
      return true
    }

    return super.onFloatingControllerBackPressed()
  }

  override fun onDisposed() {
    super.onDisposed()

    popupRepliesScreenViewModel.clearPostReplyChainStack()
  }

  enum class Anchors {
    Visible,
    Back,
    Close
  }

  sealed class ReplyViewMode : Parcelable {
    abstract val postDescriptor: PostDescriptor

    @Parcelize
    data class ReplyTo(
      override val postDescriptor: PostDescriptor
    ) : ReplyViewMode()

    @Parcelize
    data class RepliesFrom(
      override val postDescriptor: PostDescriptor
    ) : ReplyViewMode()
  }

  companion object {
    const val REPLY_VIEW_MODE = "reply_view_mode"

    private val SCREEN_KEY = ScreenKey("PopupRepliesScreen")
  }

}