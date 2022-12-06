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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupPostsScreen.Companion.buttonsHeight
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupPostsScreen.Companion.buttonsLayoutId
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupPostsScreen.Companion.postListLayoutId
import com.github.k1rakishou.kurobaexlite.features.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.thread.PostFollowStack
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPosition
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.GenericLazyStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyListStateWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.absoluteValue

class PopupPostsScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val popupPostsScreenViewModel: DefaultPopupPostsScreenViewModel by componentActivity.viewModel()
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val popupPostViewMode: PopupPostViewMode by requireArgumentLazy(REPLY_VIEW_MODE)

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  override val screenKey: ScreenKey = SCREEN_KEY

  private var _unpresentAnimation: NavigationRouter.ScreenAnimation = NavigationRouter.ScreenAnimation.Pop(screenKey)

  override val unpresentAnimation: NavigationRouter.ScreenAnimation
    get() = _unpresentAnimation

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
          Anchors.Back -> {
            _unpresentAnimation = NavigationRouter.ScreenAnimation.Fade(
              fadeType = NavigationRouter.ScreenAnimation.FadeType.Out,
              screenKey = screenKey,
              animationDuration = 32
            )

            onBackPressed()
          }
          Anchors.Close -> {
            _unpresentAnimation = NavigationRouter.ScreenAnimation.Fade(
              fadeType = NavigationRouter.ScreenAnimation.FadeType.Out,
              screenKey = screenKey,
              animationDuration = 32
            )

            stopPresenting()
          }
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
            thresholds = { _, _ -> FractionalThreshold(0.25f) }
          )
          .absoluteOffset { IntOffset(x = currentOffset.toInt(), y = 0) }
          .graphicsLayer { alpha = (1f - progress.absoluteValue) }
      ) {
        super.CardContent()
      }
    }
  }

  @Composable
  override fun DefaultFloatingScreenBackPressHandler() {
    // Disable default back press handler, we have a custom one.
  }

  @OptIn(ExperimentalMaterialApi::class)
  @Composable
  override fun FloatingContent() {
    val orientation = LocalConfiguration.current.orientation

    val postCellCommentTextSizeSp by globalUiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by globalUiInfoManager.postCellSubjectTextSizeSp.collectAsState()
    val postsAsyncDataState by popupPostsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val postListOptions by remember {
      derivedStateOf {
        PostListOptions(
          isCatalogMode = popupPostViewMode.isCatalogMode,
          showThreadStatusCell = false,
          textSelectionEnabled = true,
          isInPopup = true,
          openedFromScreenKey = screenKey,
          pullToRefreshEnabled = false,
          contentPadding = PaddingValues(),
          mainUiLayoutMode = MainUiLayoutMode.Phone,
          postCellCommentTextSizeSp = postCellCommentTextSizeSp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
          detectLinkableClicks = true,
          orientation = orientation,
          postViewMode = PostViewMode.List
        )
      }
    }

    val density = LocalDensity.current
    val buttonsHeightPx = with(density) { remember(key1 = buttonsHeight) { buttonsHeight.toPx().toInt() } }

    HandleBackPresses {
      if (popupPostsScreenViewModel.popReplyChain(screenKey)) {
        swipeableState.snapTo(Anchors.Visible)
        return@HandleBackPresses true
      }

      return@HandleBackPresses stopPresenting()
    }

    if (postsAsyncDataState !is AsyncData.Uninitialized && postsAsyncDataState !is AsyncData.Loading) {
      PopupPostsScreenContentLayout(
        popupPostViewMode = popupPostViewMode,
        postListOptions = postListOptions,
        buttonsHeightPx = buttonsHeightPx,
        screenKey = screenKey,
        postLongtapContentMenuProvider = { postLongtapContentMenu },
        linkableClickHelperProvider = { linkableClickHelper },
        stopPresenting = { stopPresenting() },
        onBackPressed = { coroutineScope.launch { onBackPressed() } },
        onPostImageClicked = { chanDescriptor, postImageData, thumbnailBoundsInRoot ->
          val collectedImages = popupPostsScreenViewModel.collectCurrentImages(screenKey, chanDescriptor)
          if (collectedImages.isEmpty()) {
            return@PopupPostsScreenContentLayout
          }

          clickedThumbnailBoundsStorage.storeBounds(postImageData, thumbnailBoundsInRoot)

          val mediaViewerScreen = ComposeScreen.createScreen<MediaViewerScreen>(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            args = {
              val mediaViewerParams = MediaViewerParams.Images(
                chanDescriptor = chanDescriptor,
                images = collectedImages.map { it.fullImageUrl },
                initialImageUrlString = postImageData.fullImageAsString
              )

              putParcelable(MediaViewerScreen.mediaViewerParamsKey, mediaViewerParams)
              putParcelable(MediaViewerScreen.openedFromScreenKey, screenKey)
            },
            callbacks = {
              callback(
                callbackKey = MediaViewerScreen.openingCatalogOrScreenCallbackKey,
                func = { stopPresenting() }
              )
            }
          )

          navigationRouter.presentScreen(mediaViewerScreen)
        }
      )
    }

    LaunchedEffect(
      key1 = popupPostViewMode,
      block = { popupPostsScreenViewModel.loadRepliesForModeInitial(screenKey, popupPostViewMode) }
    )
  }

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      popupPostsScreenViewModel.clearPostReplyChainStack(screenKey)
    }

    super.onDisposed(screenDisposeEvent)
  }

  enum class Anchors {
    Visible,
    Back,
    Close
  }

  @Immutable
  sealed class PopupPostViewMode : Parcelable {
    abstract val chanDescriptor: ChanDescriptor

    val isCatalogMode: Boolean
      get() {
        return when (this) {
          is PostList -> chanDescriptor is CatalogDescriptor
          is RepliesFrom,
          is ReplyTo -> false
        }
      }

    @Parcelize
    data class ReplyTo(
      override val chanDescriptor: ChanDescriptor,
      val postDescriptor: PostDescriptor
    ) : PopupPostViewMode()

    @Parcelize
    data class RepliesFrom(
      override val chanDescriptor: ChanDescriptor,
      val postDescriptor: PostDescriptor,
      val includeThisPost: Boolean = false
    ) : PopupPostViewMode()

    @Parcelize
    data class PostList(
      override val chanDescriptor: ChanDescriptor,
      val postNoWithSubNoList: List<Pair<Long, Long>>
    ) : PopupPostViewMode() {

      @IgnoredOnParcel
      val asPostDescriptorList by lazy {
        return@lazy postNoWithSubNoList.map { (postNo, postSubNo) ->
          when (chanDescriptor) {
            is CatalogDescriptor -> {
              PostDescriptor.create(
                threadDescriptor = ThreadDescriptor.create(chanDescriptor, postNo),
                postNo = postNo,
                postSubNo = postSubNo
              )
            }
            is ThreadDescriptor -> {
              return@map PostDescriptor.create(
                threadDescriptor = chanDescriptor,
                postNo = postNo,
                postSubNo = postSubNo
              )
            }
          }
        }
      }

    }

  }

  companion object {
    const val REPLY_VIEW_MODE = "reply_view_mode"

    private val SCREEN_KEY = ScreenKey("PopupPostsScreen")

    internal val postListLayoutId = "PopupPostsScreen_PostListContent"
    internal val buttonsLayoutId = "PopupPostsScreen_Buttons"

    internal val buttonsHeight = 50.dp
  }

}

@Composable
private fun PopupPostsScreenContentLayout(
  popupPostViewMode: PopupPostsScreen.PopupPostViewMode,
  postListOptions: PostListOptions,
  buttonsHeightPx: Int,
  screenKey: ScreenKey,
  postLongtapContentMenuProvider: () -> PostLongtapContentMenu,
  linkableClickHelperProvider: () -> LinkableClickHelper,
  stopPresenting: () -> Unit,
  onBackPressed: () -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
) {
  Layout(
    content = {
      PopupPostsScreenContent(
        popupPostViewMode = popupPostViewMode,
        postListOptions = postListOptions,
        screenKey = screenKey,
        postLongtapContentMenuProvider = postLongtapContentMenuProvider,
        linkableClickHelperProvider = linkableClickHelperProvider,
        stopPresenting = stopPresenting,
        onBackPressed = onBackPressed,
        onPostImageClicked = onPostImageClicked
      )
    },
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
private fun PopupPostsScreenContent(
  popupPostViewMode: PopupPostsScreen.PopupPostViewMode,
  postListOptions: PostListOptions,
  screenKey: ScreenKey,
  postLongtapContentMenuProvider: () -> PostLongtapContentMenu,
  linkableClickHelperProvider: () -> LinkableClickHelper,
  stopPresenting: () -> Unit,
  onBackPressed: () -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val context = LocalContext.current
  val view = LocalView.current
  val orientation by rememberUpdatedState(newValue = LocalConfiguration.current.orientation)
  val coroutineScope = rememberCoroutineScope()

  val popupPostsScreenViewModel: DefaultPopupPostsScreenViewModel = koinRememberViewModel()
  val catalogScreenViewModel: CatalogScreenViewModel = koinRememberViewModel()
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val replyLayoutViewModel: ReplyLayoutViewModel = koinRememberViewModel()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val androidHelpers: AndroidHelpers = koinRemember()
  val postFollowStack: PostFollowStack = koinRemember()

  val viewProvider by rememberUpdatedState(newValue = { view })

  val _lazyListState = rememberLazyListState()
  val lazyListStateWrapper = remember(key1 = _lazyListState) { LazyListStateWrapper(_lazyListState) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      popupPostsScreenViewModel.scrollRestorationEventFlow.collectLatest { rememberedScrollPosition ->
        if (orientation == rememberedScrollPosition.orientation) {
          try {
            delay(100)

            lazyListStateWrapper.scrollToItem(
              index = rememberedScrollPosition.index,
              scrollOffset = rememberedScrollPosition.offset
            )
          } catch (ignored: CancellationException) {
          }
        }
      }
    }
  )

  Box(
    modifier = Modifier
      .layoutId(postListLayoutId)
      .animateContentSize()
  ) {
    PostListContent(
      lazyStateWrapper = lazyListStateWrapper as GenericLazyStateWrapper,
      postListOptions = postListOptions,
      postsScreenViewModelProvider = { popupPostsScreenViewModel },
      onPostCellClicked = { postCellData -> /*no-op*/ },
      onPostCellLongClicked = { postCellData ->
        postLongtapContentMenuProvider().showMenu(
          postListOptions = postListOptions,
          postCellData = postCellData,
          viewProvider = viewProvider,
          reparsePostsFunc = { postDescriptors ->
            val chanDescriptor = if (postListOptions.isCatalogMode) {
              catalogScreenViewModel.catalogDescriptor
            } else {
              threadScreenViewModel.threadDescriptor
            }

            if (chanDescriptor == null) {
              return@showMenu
            }

            popupPostsScreenViewModel.reparsePostsByDescriptors(chanDescriptor, postDescriptors)

            // Reparse the threadScreenViewModel posts too
            threadScreenViewModel.reparsePostsByDescriptors(chanDescriptor, postDescriptors)
          }
        )
      },
      onLinkableClicked = { postCellData, linkable ->
        linkableClickHelperProvider().processClickedLinkable(
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
          showRepliesForPostFunc = { postViewMode ->
            coroutineScope.launch {
              val rememberedPosition = LazyColumnRememberedPosition(
                orientation = orientation,
                index = lazyListStateWrapper.firstVisibleItemIndex,
                offset = lazyListStateWrapper.firstVisibleItemScrollOffset
              )

              popupPostsScreenViewModel.loadRepliesForMode(
                screenKey = screenKey,
                popupPostViewMode = postViewMode,
                rememberedPosition = rememberedPosition
              )
            }
          }
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
        if (popupPostViewMode is PopupPostsScreen.PopupPostViewMode.PostList) {
          return@PostListContent
        }

        coroutineScope.launch {
          val rememberedPosition = LazyColumnRememberedPosition(
            orientation = orientation,
            index = lazyListStateWrapper.firstVisibleItemIndex,
            offset = lazyListStateWrapper.firstVisibleItemScrollOffset
          )

          popupPostsScreenViewModel.loadRepliesForMode(
            screenKey = screenKey,
            popupPostViewMode = PopupPostsScreen.PopupPostViewMode.RepliesFrom(
              chanDescriptor = chanDescriptor,
              postDescriptor = postDescriptor
            ),
            rememberedPosition = rememberedPosition
          )
        }
      },
      onCopySelectedText = { selectedText ->
        androidHelpers.copyToClipboard("Selected text", selectedText)
      },
      onQuoteSelectedText = { withText, selectedText, postCellData ->
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@PostListContent

        if (withText) {
          replyLayoutViewModel.quotePostWithText(threadDescriptor, postCellData, selectedText)
        } else {
          replyLayoutViewModel.quotePost(threadDescriptor, postCellData)
        }
      },
      onPostImageClicked = onPostImageClicked,
      onGoToPostClicked = { postCellData ->
        if (postListOptions.isCatalogMode) {
          threadScreenViewModel.loadThread(postCellData.postDescriptor.threadDescriptor)
          globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
        } else {
          threadScreenViewModel.scrollToPost(
            postDescriptor = postCellData.postDescriptor,
            blink = true
          )

          processPostFollow(
            popupPostsScreenViewModel = popupPostsScreenViewModel,
            screenKey = screenKey,
            postFollowStack = postFollowStack
          )
        }

        val parentScreenKey = if (postListOptions.isCatalogMode) {
          CatalogScreen.SCREEN_KEY
        } else {
          ThreadScreen.SCREEN_KEY
        }

        globalUiInfoManager.getOrCreateHideableUiVisibilityInfo(parentScreenKey)
          .update(contentListScrollState = 1f)

        stopPresenting()
      },
      onPostListScrolled = { delta -> /*no-op*/ },
      onPostListTouchingTopOrBottomStateChanged = { touching -> /*no-op*/ },
      onCurrentlyTouchingPostList = { touching -> /*no-op*/ },
      onFastScrollerDragStateChanged = { dragging -> /*no-op*/ },
      loadingContent = { lazyItemScope, isInPopup -> /*no-op*/ },
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

private fun processPostFollow(
  popupPostsScreenViewModel: DefaultPopupPostsScreenViewModel,
  screenKey: ScreenKey,
  postFollowStack: PostFollowStack
) {
  val postDataState = popupPostsScreenViewModel.postDataStateMap[screenKey]
  if (postDataState != null) {
    val topEntry = postDataState.postReplyChainStack.lastOrNull()

    val postDescriptor = when (topEntry) {
      is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> topEntry.postDescriptor
      is PopupPostsScreen.PopupPostViewMode.ReplyTo -> topEntry.postDescriptor
      is PopupPostsScreen.PopupPostViewMode.PostList,
      null -> null
    }

    if (postDescriptor != null) {
      @Suppress("KotlinConstantConditions") val popupType = when (topEntry) {
        is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> PostFollowStack.Entry.PopupInfo.Type.RepliesFrom
        is PopupPostsScreen.PopupPostViewMode.ReplyTo -> PostFollowStack.Entry.PopupInfo.Type.ReplyTo
        is PopupPostsScreen.PopupPostViewMode.PostList,
        null -> unreachable()
      }

      postFollowStack.push(
        postDescriptor = postDescriptor,
        popupInfo = PostFollowStack.Entry.PopupInfo(
          type = popupType
        )
      )
    }
  }
}