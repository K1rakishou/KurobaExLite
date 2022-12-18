package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.PostBlinkAnimationState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.PostCell
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.PostCellContainerAnimated
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.canAnimateInsertion
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.canAnimateUpdate
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.rememberPostBlinkAnimationState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPartSpan
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeErrorWithButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.GenericLazyStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyGridStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyListStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.detectListScrollEvents
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import kotlinx.coroutines.delay


private const val postCellKeyPrefix = "post_cell"

@Composable
internal fun PostListContent(
  modifier: Modifier = Modifier,
  lazyStateWrapper: GenericLazyStateWrapper,
  postListOptions: PostListOptions,
  postListSelectionState: PostListSelectionState,
  postsScreenViewModelProvider: () -> PostScreenViewModel,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onLinkableClicked: (PostCellData, TextPartSpan.Linkable) -> Unit,
  onLinkableLongClicked: (PostCellData, TextPartSpan.Linkable) -> Unit,
  onPostRepliesClicked: (ChanDescriptor, PostDescriptor) -> Unit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListTouchingTopOrBottomStateChanged: (Boolean) -> Unit,
  onCurrentlyTouchingPostList: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  onGoToPostClicked: ((PostCellData) -> Unit)?,
  emptyContent: @Composable (Any, Boolean, Boolean) -> Unit = { lazyItemScope, isInPopup, isCatalogMode ->
    PostListEmptyContent(lazyItemScope, isInPopup, isCatalogMode)
  },
  loadingContent: @Composable (Any, Boolean) -> Unit = { lazyItemScope, isInPopup ->
    PostListLoadingContent(lazyItemScope, isInPopup)
  },
  errorContent: @Composable (Any, AsyncData.Error, Boolean) -> Unit = { lazyItemScope, postListAsyncError, isInPopup ->
    PostListErrorContent(lazyItemScope, postListAsyncError, isInPopup, postsScreenViewModelProvider)
  },
) {
  val postsScreenViewModel = postsScreenViewModelProvider()
  val chanDescriptorMut by postsScreenViewModel.postScreenState.chanDescriptorFlow.collectAsState()
  val chanDescriptor = chanDescriptorMut

  if (chanDescriptor == null) {
    if (postListOptions.isInPopup) {
      return
    }

    val isCatalog = postsScreenViewModel is CatalogScreenViewModel
    DisplayCatalogOrThreadNotSelectedPlaceholder(isCatalog)
    return
  }

  val openedFromScreenKey = postListOptions.openedFromScreenKey
  val postListAsyncMut by postsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
  val postListAsync = postListAsyncMut
  val postListAsyncUpdated by rememberUpdatedState(newValue = postListAsync)

  val lazyStateWrapperUpdated by rememberUpdatedState(newValue = lazyStateWrapper)
  val postBlinkAnimationState = rememberPostBlinkAnimationState()

  fun processPostListScrollEventFunc() {
    processPostListScrollEvent(
      postsScreenViewModel = postsScreenViewModel,
      postListAsync = postListAsyncUpdated,
      lazyStateWrapper = lazyStateWrapperUpdated,
      chanDescriptor = chanDescriptor,
      orientation = postListOptions.orientation,
      isInPopup = postListOptions.isInPopup,
      onPostListTouchingTopOrBottomStateChanged = onPostListTouchingTopOrBottomStateChanged
    )
  }

  if (postListAsync is AsyncData.Data) {
    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        postsScreenViewModel.scrollRestorationEventFlow.collect { lastRememberedPosition ->
          lazyStateWrapperUpdated.scrollToItem(
            index = lastRememberedPosition.index,
            scrollOffset = lastRememberedPosition.offset
          )

          processPostListScrollEventFunc()

          if (lastRememberedPosition.blinkPostDescriptor != null) {
            postBlinkAnimationState.startBlinking(lastRememberedPosition.blinkPostDescriptor)
          }
        }
      })

    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        postsScreenViewModel.postListScrollEventFlow.collect { toolbarScrollEvent ->
          val positionToScroll = when (toolbarScrollEvent) {
            PostScreenViewModel.ToolbarScrollEvent.ScrollBottom -> {
              lazyStateWrapperUpdated.totalItemsCount
            }
            PostScreenViewModel.ToolbarScrollEvent.ScrollTop -> {
              0
            }
            is PostScreenViewModel.ToolbarScrollEvent.ScrollToItem -> {
              val postState = (postListAsyncUpdated as? AsyncData.Data)?.data
                ?: return@collect

              postState.postIndexByPostDescriptor(toolbarScrollEvent.postDescriptor)
            }
          }

          if (positionToScroll == null) {
            return@collect
          }

          lazyStateWrapperUpdated.scrollToItem(
            index = positionToScroll,
            scrollOffset = 0
          )

          processPostListScrollEventFunc()

          if (toolbarScrollEvent is PostScreenViewModel.ToolbarScrollEvent.ScrollToItem && toolbarScrollEvent.blink) {
            postBlinkAnimationState.startBlinking(toolbarScrollEvent.postDescriptor)
          }
        }
      })

    LaunchedEffect(
      key1 = chanDescriptor,
      key2 = postListAsync,
      key3 = openedFromScreenKey,
      block = {
        val postsState = postListAsync.data

        postsScreenViewModel.mediaViewerScrollEvents.collect { scrollInfo ->
          if (openedFromScreenKey != scrollInfo.screenKey) {
            return@collect
          }

          val indexToScroll = postsState.postIndexByPostDescriptor(scrollInfo.postDescriptor)
          if (indexToScroll != null) {
            lazyStateWrapperUpdated.scrollToItem(
              index = indexToScroll,
              scrollOffset = 0
            )

            processPostListScrollEventFunc()
          }
        }
      }
    )
  }

  DisposableEffect(
    key1 = Unit,
    effect = {
      onDispose { postsScreenViewModelProvider().onPostListDisposed() }
    }
  )

  PostListInternal(
    modifier = modifier,
    chanDescriptor = chanDescriptor,
    lazyStateWrapper = lazyStateWrapperUpdated as GenericLazyStateWrapper,
    postBlinkAnimationState = postBlinkAnimationState,
    postListOptions = postListOptions,
    postListSelectionState = postListSelectionState,
    postListAsync = postListAsync,
    postsScreenViewModelProvider = postsScreenViewModelProvider,
    onPostCellClicked = onPostCellClicked,
    onPostCellLongClicked = onPostCellLongClicked,
    onPostCellCommentClicked = { postCellData: PostCellData, postComment: AnnotatedString, offset: Int ->
      processClickedAnnotation(
        postCellData = postCellData,
        postComment = postComment,
        characterOffset = offset,
        longClicked = false,
        reparsePost = { postCellData, parsedPostDataContext ->
          postsScreenViewModel.reparsePost(postCellData, parsedPostDataContext)
        },
        onLinkableClicked = onLinkableClicked,
        onLinkableLongClicked = onLinkableLongClicked
      )
    },
    onPostCellCommentLongClicked = { postCellData: PostCellData, postComment: AnnotatedString, offset: Int ->
      processClickedAnnotation(
        postCellData = postCellData,
        postComment = postComment,
        characterOffset = offset,
        longClicked = true,
        reparsePost = { postCellData, parsedPostDataContext ->
          postsScreenViewModel.reparsePost(postCellData, parsedPostDataContext)
        },
        onLinkableClicked = onLinkableClicked,
        onLinkableLongClicked = onLinkableLongClicked
      )
    },
    onPostRepliesClicked = { postCellData ->
      onPostRepliesClicked(chanDescriptor, postCellData.postDescriptor)
    },
    onThreadStatusCellClicked = {
      postsScreenViewModel.resetTimer()
      postsScreenViewModel.refresh()
    },
    onCopySelectedText = onCopySelectedText,
    onQuoteSelectedText = onQuoteSelectedText,
    onPostListScrolled = { scrollDelta ->
      processPostListScrollEventFunc()
      onPostListScrolled(scrollDelta)
    },
    onCurrentlyTouchingPostList = onCurrentlyTouchingPostList,
    onFastScrollerDragStateChanged = { isDraggingFastScroller ->
      if (!isDraggingFastScroller) {
        processPostListScrollEventFunc()
      }

      onFastScrollerDragStateChanged(isDraggingFastScroller)
    },
    onPostImageClicked = onPostImageClicked,
    onPostImageLongClicked = onPostImageLongClicked,
    onGoToPostClicked = onGoToPostClicked,
    emptyContent = emptyContent,
    loadingContent = loadingContent,
    errorContent = errorContent,
  )

  if (postListAsync is AsyncData.Data && !postListOptions.isInPopup) {
    val hasVisibleItems by remember { derivedStateOf { lazyStateWrapperUpdated.layoutInfo.visibleItemsInfo.isNotEmpty() } }
    // Call processPostListScrollEventFunc() as soon as we get the actual post data so that we can
    // reset the last seen post indicator in threads with few posts that fit the whole screen without
    // having to scroll it. Otherwise the indicator will stay (since we can't scroll the thread and
    // we only ever update it on scroll events).
    LaunchedEffect(
      key1 = chanDescriptor,
      key2 = hasVisibleItems,
      block = {
        if (!hasVisibleItems) {
          return@LaunchedEffect
        }

        // Wait for a bit for the UI stuff to load fully
        delay(100)

        processPostListScrollEventFunc()
      }
    )

    val firstVisibleElementKey by remember {
      derivedStateOf { lazyStateWrapperUpdated.layoutInfo.visibleItemsInfo.firstOrNull()?.key }
    }

    val firstPostDrawn = remember(
      key1 = firstVisibleElementKey,
      key2 = postListOptions.orientation
    ) {
      return@remember (firstVisibleElementKey as? String)
        ?.startsWith(postCellKeyPrefix)
        ?: false
    }

    RestoreScrollPosition(
      firstPostDrawn = firstPostDrawn,
      chanDescriptor = chanDescriptor,
      orientation = postListOptions.orientation,
      postsScreenViewModelProvider = postsScreenViewModelProvider
    )
  }
}

private fun processPostListScrollEvent(
  postsScreenViewModel: PostScreenViewModel,
  postListAsync: AsyncData<PostsState>,
  lazyStateWrapper: GenericLazyStateWrapper,
  chanDescriptor: ChanDescriptor,
  orientation: Int,
  isInPopup: Boolean,
  onPostListTouchingTopOrBottomStateChanged: (Boolean) -> Unit
) {
  if (isInPopup) {
    return
  }

  if (postListAsync !is AsyncData.Data) {
    return
  }

  val firstVisibleItem = lazyStateWrapper.layoutInfo.visibleItemsInfo.firstOrNull()
  val lastVisibleItem = lazyStateWrapper.layoutInfo.visibleItemsInfo.lastOrNull()
  val maxAllowedOffset = 64

  if (firstVisibleItem != null && lastVisibleItem != null) {
    val firstVisibleItemIndex = firstVisibleItem.index
    val firstVisibleItemOffset = firstVisibleItem.offsetY
    val lastVisibleItemIndex = lastVisibleItem.index

    val totalCount = lazyStateWrapper.layoutInfo.totalItemsCount
    val touchingTop = firstVisibleItemIndex <= 0 && firstVisibleItemOffset in 0..maxAllowedOffset
    val touchingBottom = lastVisibleItemIndex >= (totalCount - 1)

    onPostListTouchingTopOrBottomStateChanged(touchingTop || touchingBottom)
  }

  val postDataList = (postListAsync as? AsyncData.Data)?.data?.postsCopy
  val visibleItemsInfo = lazyStateWrapper.layoutInfo.visibleItemsInfo

  if (postDataList != null) {
    var firstCompletelyVisibleItem = visibleItemsInfo.firstOrNull { it.offsetY >= 0 }
    if (firstCompletelyVisibleItem == null) {
      firstCompletelyVisibleItem = visibleItemsInfo.firstOrNull()
    }

    var lastCompletelyVisibleItem = visibleItemsInfo.lastOrNull()
    if (lastCompletelyVisibleItem?.key == threadStatusCellKey) {
      lastCompletelyVisibleItem = visibleItemsInfo.getOrNull(visibleItemsInfo.lastIndex - 1)
    }

    val firstVisiblePostData = firstCompletelyVisibleItem?.let { item -> postDataList.getOrNull(item.index) }
    val lastVisiblePostData = lastCompletelyVisibleItem?.let { item -> postDataList.getOrNull(item.index) }
    val postListTouchingBottom = visibleItemsInfo.lastOrNull()?.key == threadStatusCellKey

    if (firstVisiblePostData != null && lastVisiblePostData != null) {
      postsScreenViewModel.onPostScrollChanged(
        firstVisiblePostData = firstVisiblePostData,
        lastVisiblePostData = lastVisiblePostData,
        postListTouchingBottom = postListTouchingBottom
      )
    }
  }

  if (lazyStateWrapper.layoutInfo.visibleItemsInfo.isNotEmpty()) {
    val firstVisibleItemIndex = lazyStateWrapper.firstVisibleItemIndex
    val firstVisibleItemScrollOffset = lazyStateWrapper.firstVisibleItemScrollOffset

    postsScreenViewModel.rememberPosition(
      chanDescriptor = chanDescriptor,
      index = firstVisibleItemIndex,
      offset = firstVisibleItemScrollOffset,
      orientation = orientation
    )
  }
}

@Composable
private fun PostListInternal(
  modifier: Modifier,
  chanDescriptor: ChanDescriptor,
  lazyStateWrapper: GenericLazyStateWrapper,
  postBlinkAnimationState: PostBlinkAnimationState,
  postListOptions: PostListOptions,
  postListSelectionState: PostListSelectionState,
  postListAsync: AsyncData<PostsState>,
  postsScreenViewModelProvider: () -> PostScreenViewModel,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  onGoToPostClicked: ((PostCellData) -> Unit)?,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onCurrentlyTouchingPostList: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
  emptyContent: @Composable ((Any, Boolean, Boolean) -> Unit),
  loadingContent: @Composable ((Any, Boolean) -> Unit),
  errorContent: @Composable ((Any, AsyncData.Error, Boolean) -> Unit),
) {
  val isInPopup = postListOptions.isInPopup
  val pullToRefreshEnabled = postListOptions.pullToRefreshEnabled
  val isCatalogMode = postListOptions.isCatalogMode
  val showThreadStatusCell = postListOptions.showThreadStatusCell
  val textSelectionEnabled = postListOptions.textSelectionEnabled
  val cellsPadding = remember { PaddingValues(horizontal = 8.dp) }

  val postsScreenViewModel = postsScreenViewModelProvider()
  val lastViewedPostDescriptorForIndicator by postsScreenViewModel.postScreenState.lastViewedPostForIndicator.collectAsState()
  val currentlyOpenedThread by postsScreenViewModel.currentlyOpenedThreadFlow.collectAsState()
  val searchQuery by postsScreenViewModel.postScreenState.searchQueryFlow.collectAsState()

  val contentPadding = remember(key1 = searchQuery, key2 = postListOptions.contentPadding) {
    if (searchQuery.isNullOrEmpty()) {
      postListOptions.contentPadding
    } else {
      PaddingValues(
        start = postListOptions.contentPadding.calculateStartPadding(LayoutDirection.Ltr),
        end = postListOptions.contentPadding.calculateEndPadding(LayoutDirection.Ltr),
        top = postListOptions.contentPadding.calculateTopPadding(),
        bottom = postListOptions.contentPadding.calculateBottomPadding(),
      )
    }
  }

  val pullToRefreshTopPaddingDp = remember(key1 = contentPadding) { contentPadding.calculateTopPadding() }
  val pullToRefreshState = rememberPullToRefreshState()

  val buildThreadStatusCellFunc: @Composable (() -> Unit)? = if (
    showThreadStatusCell &&
    searchQuery == null &&
    postsScreenViewModel is ThreadScreenViewModel
  ) {
    {
      ThreadStatusCell(
        padding = cellsPadding,
        threadScreenViewModelProvider = { postsScreenViewModel },
        onThreadStatusCellClicked = onThreadStatusCellClicked
      )
    }
  } else {
    null
  }

  Box {
    PullToRefresh(
      pullToRefreshEnabled = pullToRefreshEnabled,
      topPadding = pullToRefreshTopPaddingDp,
      pullToRefreshState = pullToRefreshState,
      canPull = { searchQuery == null },
      onTriggered = {
        postsScreenViewModel.reload(
          loadOptions = PostScreenViewModel.LoadOptions(
            showLoadingIndicator = false,
            forced = true
          ),
          onReloadFinished = {
            pullToRefreshState.stopRefreshing()
            postsScreenViewModel.scrollTop()
          }
        )
      }
    ) {
      when (postListOptions.postViewMode) {
        PostViewMode.List -> {
          PostsListMode(
            modifier = modifier,
            isInPopup = isInPopup,
            isCatalogMode = isCatalogMode,
            textSelectionEnabled = textSelectionEnabled,
            chanDescriptor = chanDescriptor,
            currentlyOpenedThread = currentlyOpenedThread,
            contentPadding = contentPadding,
            cellsPadding = cellsPadding,
            postListOptions = postListOptions,
            postListSelectionState = postListSelectionState,
            postListAsync = postListAsync,
            lazyListStateWrapper = lazyStateWrapper as LazyListStateWrapper,
            postBlinkAnimationState = postBlinkAnimationState,
            lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
            onCopySelectedText = onCopySelectedText,
            onQuoteSelectedText = onQuoteSelectedText,
            onPostCellClicked = onPostCellClicked,
            onPostCellLongClicked = onPostCellLongClicked,
            onPostCellCommentClicked = onPostCellCommentClicked,
            onPostCellCommentLongClicked = onPostCellCommentLongClicked,
            onPostRepliesClicked = onPostRepliesClicked,
            onPostImageClicked = onPostImageClicked,
            onPostImageLongClicked = onPostImageLongClicked,
            onGoToPostClicked = onGoToPostClicked,
            onFastScrollerDragStateChanged = onFastScrollerDragStateChanged,
            onPostListScrolled = onPostListScrolled,
            onCurrentlyTouchingPostList = onCurrentlyTouchingPostList,
            buildThreadStatusCellFunc = buildThreadStatusCellFunc,
            emptyContent = emptyContent,
            loadingContent = loadingContent,
            errorContent = errorContent,
            postsScreenViewModelProvider = postsScreenViewModelProvider,
          )
        }
        PostViewMode.Grid -> {
          PostsGridMode(
            modifier = modifier,
            isInPopup = isInPopup,
            isCatalogMode = isCatalogMode,
            textSelectionEnabled = textSelectionEnabled,
            chanDescriptor = chanDescriptor,
            currentlyOpenedThread = currentlyOpenedThread,
            contentPadding = contentPadding,
            cellsPadding = cellsPadding,
            postListOptions = postListOptions,
            postListSelectionState = postListSelectionState,
            postListAsync = postListAsync,
            lazyGridStateWrapper = lazyStateWrapper as LazyGridStateWrapper,
            postBlinkAnimationState = postBlinkAnimationState,
            lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
            onCopySelectedText = onCopySelectedText,
            onQuoteSelectedText = onQuoteSelectedText,
            onPostCellClicked = onPostCellClicked,
            onPostCellLongClicked = onPostCellLongClicked,
            onPostCellCommentClicked = onPostCellCommentClicked,
            onPostCellCommentLongClicked = onPostCellCommentLongClicked,
            onPostRepliesClicked = onPostRepliesClicked,
            onPostImageClicked = onPostImageClicked,
            onPostImageLongClicked = onPostImageLongClicked,
            onGoToPostClicked = onGoToPostClicked,
            onFastScrollerDragStateChanged = onFastScrollerDragStateChanged,
            onPostListScrolled = onPostListScrolled,
            onCurrentlyTouchingPostList = onCurrentlyTouchingPostList,
            buildThreadStatusCellFunc = buildThreadStatusCellFunc,
            emptyContent = emptyContent,
            loadingContent = loadingContent,
            errorContent = errorContent,
            postsScreenViewModelProvider = postsScreenViewModelProvider,
          )
        }
      }
    }
  }
}

@Composable
private fun PostsListMode(
  modifier: Modifier,
  isInPopup: Boolean,
  isCatalogMode: Boolean,
  textSelectionEnabled: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  contentPadding: PaddingValues,
  cellsPadding: PaddingValues,
  postListOptions: PostListOptions,
  postListSelectionState: PostListSelectionState,
  postListAsync: AsyncData<PostsState>,
  lazyListStateWrapper: LazyListStateWrapper,
  postBlinkAnimationState: PostBlinkAnimationState,
  lastViewedPostDescriptorForIndicator: PostDescriptor?,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  onGoToPostClicked: ((PostCellData) -> Unit)?,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onCurrentlyTouchingPostList: (Boolean) -> Unit,
  buildThreadStatusCellFunc: @Composable (() -> Unit)?,
  emptyContent: @Composable ((Any, Boolean, Boolean) -> Unit),
  loadingContent: @Composable ((Any, Boolean) -> Unit),
  errorContent: @Composable ((Any, AsyncData.Error, Boolean) -> Unit),
  postsScreenViewModelProvider: () -> PostScreenViewModel,
) {
  val postsScreenViewModel = postsScreenViewModelProvider()
  val fastScrollerMarks by postsScreenViewModel.fastScrollerMarksFlow.collectAsState()

  LazyColumnWithFastScroller(
    lazyListContainerModifier = modifier.then(
      Modifier
        .detectListScrollEvents(
          token = "onPostListScrolled",
          onListScrolled = { delta -> onPostListScrolled(delta) }
        )
        .pointerInput(
          key1 = Unit,
          block = {
            detectTouches { touching -> onCurrentlyTouchingPostList(touching) }
          }
        )
    ),
    lazyListState = lazyListStateWrapper.lazyListState,
    contentPadding = contentPadding,
    fastScrollerMarks = fastScrollerMarks,
    onFastScrollerDragStateChanged = { dragging -> onFastScrollerDragStateChanged(dragging) },
    content = {
      postListAsyncDataContent(
        postListAsync = postListAsync,
        emptyContent = { lazyItemScope ->
          emptyContent(lazyItemScope, isInPopup, isCatalogMode)
        },
        loadingContent = { lazyItemScope ->
          loadingContent(lazyItemScope, isInPopup)
        },
        errorContent = { lazyItemScope, postListAsyncError ->
          errorContent(lazyItemScope, postListAsyncError, isInPopup)
        },
        dataContent = { postListAsyncData ->
          val abstractPostsState = postListAsyncData.data
          val previousPostDataInfoMap = abstractPostsState.postListAnimationInfoMap
          val postCellDataList by abstractPostsState.postsForUi

          items(
            count = postCellDataList.size,
            key = { index -> "${postCellKeyPrefix}_${postCellDataList[index].postDescriptor}" },
            itemContent = { index ->
              val postCellData = postCellDataList[index]

              var rememberedHashForUpdateAnimation by remember {
                mutableStateOf(postCellData.postServerDataHashForListAnimations)
              }

              val animateInsertion = remember(
                postCellData.postDescriptor,
                postListOptions.postViewMode
              ) {
                return@remember canAnimateInsertion(
                  previousPostDataInfoMap = previousPostDataInfoMap,
                  postCellData = postCellData,
                  searchQuery = postsScreenViewModel.postScreenState.currentSearchQuery,
                  inPopup = isInPopup,
                  postsParsedOnce = postsScreenViewModel.postsFullyParsedOnceFlow.value
                )
              }

              val animateUpdate = remember(
                postCellData.postDescriptor,
                rememberedHashForUpdateAnimation,
                postCellData.postServerDataHashForListAnimations,
                postListOptions.postViewMode
              ) {
                return@remember canAnimateUpdate(
                  previousPostDataInfoMap = previousPostDataInfoMap,
                  postCellData = postCellData,
                  searchQuery = postsScreenViewModel.postScreenState.currentSearchQuery,
                  inPopup = isInPopup,
                  rememberedHashForListAnimations = postCellData.postServerDataHashForListAnimations,
                  postsParsedOnce = postsScreenViewModel.postsFullyParsedOnceFlow.value
                )
              }

              val reparsePostSubjectRemembered = remember(key1 = postCellData) {
                { postCellData: PostCellData, onPostSubjectParsed: (AnnotatedString?) -> Unit ->
                  postsScreenViewModel.reparsePostSubject(
                    postCellData = postCellData,
                    onPostSubjectParsed = onPostSubjectParsed
                  )
                }
              }

              PostCellContainer(
                cellsPadding = cellsPadding,
                isCatalogMode = isCatalogMode,
                textSelectionEnabled = textSelectionEnabled,
                chanDescriptor = chanDescriptor,
                currentlyOpenedThread = currentlyOpenedThread,
                isInPopup = isInPopup,
                postCellData = postCellData,
                postListOptions = postListOptions,
                postListSelectionState = postListSelectionState,
                postBlinkAnimationState = postBlinkAnimationState,
                index = index,
                totalCount = postCellDataList.size,
                animateInsertion = animateInsertion,
                animateUpdate = animateUpdate,
                lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
                onPostBind = { postCellData -> postsScreenViewModel.onPostBind(postCellData) },
                onPostUnbind = { postCellData -> postsScreenViewModel.onPostUnbind(postCellData) },
                onCopySelectedText = onCopySelectedText,
                onQuoteSelectedText = onQuoteSelectedText,
                onPostCellClicked = onPostCellClicked,
                onPostCellLongClicked = onPostCellLongClicked,
                onPostCellCommentClicked = onPostCellCommentClicked,
                onPostCellCommentLongClicked = onPostCellCommentLongClicked,
                onPostRepliesClicked = onPostRepliesClicked,
                onPostImageClicked = onPostImageClicked,
                onPostImageLongClicked = onPostImageLongClicked,
                onGoToPostClicked = onGoToPostClicked,
                reparsePostSubject = reparsePostSubjectRemembered
              )

              SideEffect { rememberedHashForUpdateAnimation = postCellData.postServerDataHashForListAnimations }
            }
          )

          if (buildThreadStatusCellFunc != null) {
            item(key = threadStatusCellKey) {
              buildThreadStatusCellFunc()
            }
          }
        }
      )
    }
  )
}

@Composable
private fun PostsGridMode(
  modifier: Modifier,
  isInPopup: Boolean,
  isCatalogMode: Boolean,
  textSelectionEnabled: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  contentPadding: PaddingValues,
  cellsPadding: PaddingValues,
  postListOptions: PostListOptions,
  postListSelectionState: PostListSelectionState,
  postListAsync: AsyncData<PostsState>,
  lazyGridStateWrapper: LazyGridStateWrapper,
  postBlinkAnimationState: PostBlinkAnimationState,
  lastViewedPostDescriptorForIndicator: PostDescriptor?,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  onGoToPostClicked: ((PostCellData) -> Unit)?,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onCurrentlyTouchingPostList: (Boolean) -> Unit,
  buildThreadStatusCellFunc: @Composable (() -> Unit)?,
  emptyContent: @Composable ((Any, Boolean, Boolean) -> Unit),
  loadingContent: @Composable ((Any, Boolean) -> Unit),
  errorContent: @Composable ((Any, AsyncData.Error, Boolean) -> Unit),
  postsScreenViewModelProvider: () -> PostScreenViewModel,
) {
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val postsScreenViewModel = postsScreenViewModelProvider()

  val fastScrollerMarks by postsScreenViewModel.fastScrollerMarksFlow.collectAsState()
  val catalogGridModeColumnCount by globalUiInfoManager.catalogGridModeColumnCount.collectAsState()

  val columns = remember(key1 = catalogGridModeColumnCount) {
    if (catalogGridModeColumnCount <= 0) {
      GridCells.Adaptive(minSize = 140.dp)
    } else {
      GridCells.Fixed(count = catalogGridModeColumnCount)
    }
  }

  LazyVerticalGridWithFastScroller(
    lazyGridContainerModifier = modifier.then(
      Modifier
        .detectListScrollEvents(
          token = "onPostGridScrolled",
          onListScrolled = { delta -> onPostListScrolled(delta) }
        )
        .pointerInput(
          key1 = Unit,
          block = {
            detectTouches { touching -> onCurrentlyTouchingPostList(touching) }
          }
        )
    ),
    columns = columns,
    lazyGridState = lazyGridStateWrapper.lazyGridState,
    contentPadding = contentPadding,
    fastScrollerMarks = fastScrollerMarks,
    onFastScrollerDragStateChanged = { dragging -> onFastScrollerDragStateChanged(dragging) },
    content = {
      postGridAsyncDataContent(
        postListAsync = postListAsync,
        emptyContent = { lazyGridItemScope ->
          emptyContent(lazyGridItemScope, isInPopup, isCatalogMode)
        },
        loadingContent = { lazyGridItemScope ->
          loadingContent(lazyGridItemScope, isInPopup)
        },
        errorContent = { lazyGridItemScope, postListAsyncError ->
          errorContent(lazyGridItemScope, postListAsyncError, isInPopup)
        },
        dataContent = { postListAsyncData ->
          val abstractPostsState = postListAsyncData.data
          val previousPostDataInfoMap = abstractPostsState.postListAnimationInfoMap
          val postCellDataList by abstractPostsState.postsForUi

          items(
            count = postCellDataList.size,
            key = { index -> "${postCellKeyPrefix}_${postCellDataList[index].postDescriptor}" },
            itemContent = { index ->
              val postCellData = postCellDataList[index]

              var rememberedHashForUpdateAnimation by remember {
                mutableStateOf(postCellData.postServerDataHashForListAnimations)
              }

              val animateInsertion = remember(
                postCellData.postDescriptor,
                postListOptions.postViewMode
              ) {
                return@remember canAnimateInsertion(
                  previousPostDataInfoMap = previousPostDataInfoMap,
                  postCellData = postCellData,
                  searchQuery = postsScreenViewModel.postScreenState.currentSearchQuery,
                  inPopup = isInPopup,
                  postsParsedOnce = postsScreenViewModel.postsFullyParsedOnceFlow.value
                )
              }

              val animateUpdate = remember(
                postCellData.postDescriptor,
                rememberedHashForUpdateAnimation,
                postCellData.postServerDataHashForListAnimations,
                postListOptions.postViewMode
              ) {
                return@remember canAnimateUpdate(
                  previousPostDataInfoMap = previousPostDataInfoMap,
                  postCellData = postCellData,
                  searchQuery = postsScreenViewModel.postScreenState.currentSearchQuery,
                  inPopup = isInPopup,
                  rememberedHashForListAnimations = postCellData.postServerDataHashForListAnimations,
                  postsParsedOnce = postsScreenViewModel.postsFullyParsedOnceFlow.value
                )
              }

              PostCellContainer(
                cellsPadding = cellsPadding,
                isCatalogMode = isCatalogMode,
                textSelectionEnabled = textSelectionEnabled,
                chanDescriptor = chanDescriptor,
                currentlyOpenedThread = currentlyOpenedThread,
                isInPopup = isInPopup,
                postCellData = postCellData,
                postListOptions = postListOptions,
                postListSelectionState = postListSelectionState,
                postBlinkAnimationState = postBlinkAnimationState,
                index = index,
                totalCount = postCellDataList.size,
                animateInsertion = animateInsertion,
                animateUpdate = animateUpdate,
                lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
                onPostBind = { postCellData -> postsScreenViewModel.onPostBind(postCellData) },
                onPostUnbind = { postCellData -> postsScreenViewModel.onPostUnbind(postCellData) },
                onCopySelectedText = onCopySelectedText,
                onQuoteSelectedText = onQuoteSelectedText,
                onPostCellClicked = onPostCellClicked,
                onPostCellLongClicked = onPostCellLongClicked,
                onPostCellCommentClicked = onPostCellCommentClicked,
                onPostCellCommentLongClicked = onPostCellCommentLongClicked,
                onPostRepliesClicked = onPostRepliesClicked,
                onPostImageClicked = onPostImageClicked,
                onPostImageLongClicked = onPostImageLongClicked,
                onGoToPostClicked = onGoToPostClicked,
                reparsePostSubject = { postCellData, onPostSubjectParsed ->
                  postsScreenViewModel.reparsePostSubject(
                    postCellData = postCellData,
                    onPostSubjectParsed = onPostSubjectParsed
                  )
                }
              )

              SideEffect { rememberedHashForUpdateAnimation = postCellData.postServerDataHashForListAnimations }
            }
          )

          if (buildThreadStatusCellFunc != null) {
            item(key = threadStatusCellKey) {
              buildThreadStatusCellFunc()
            }
          }
        }
      )
    }
  )
}

@Composable
private fun PostListErrorContent(
  lazyItemScope: Any,
  postListAsyncError: AsyncData.Error,
  isInPopup: Boolean,
  postsScreenViewModelProvider: () -> PostScreenViewModel,
) {
  val postsScreenViewModel = postsScreenViewModelProvider()

  val errorMessage = remember(key1 = postListAsyncError) {
    postListAsyncError.error.errorMessageOrClassName(userReadable = true)
  }

  val sizeModifier = if (isInPopup) {
    Modifier
      .fillMaxWidth()
      .height(180.dp)
  } else {
    when (lazyItemScope) {
      is LazyItemScope -> {
        with(lazyItemScope) {
          Modifier.fillParentMaxSize()
        }
      }
      is LazyGridItemScope -> {
        Modifier.fillMaxSize()
      }
      else -> {
        error("Unknown lazyItemScope: ${lazyItemScope::class.java.simpleName}")
      }
    }
  }

  KurobaComposeErrorWithButton(
    modifier = Modifier
      .then(sizeModifier)
      .padding(8.dp),
    errorMessage = errorMessage,
    buttonText = stringResource(R.string.reload),
    onButtonClicked = { postsScreenViewModel.reload(PostScreenViewModel.LoadOptions(deleteCached = true)) }
  )
}

@Composable
private fun PostListLoadingContent(
  lazyItemScope: Any,
  isInPopup: Boolean
) {
  val sizeModifier = if (isInPopup) {
    Modifier
      .fillMaxWidth()
      .height(180.dp)
  } else {
    when (lazyItemScope) {
      is LazyItemScope -> {
        with(lazyItemScope) {
          Modifier.fillParentMaxSize()
        }
      }
      is LazyGridItemScope -> {
        Modifier.fillMaxSize()
      }
      else -> {
        error("Unknown lazyItemScope: ${lazyItemScope::class.java.simpleName}")
      }
    }
  }

  KurobaComposeLoadingIndicator(
    modifier = Modifier
      .then(sizeModifier)
      .padding(8.dp)
  )
}

@Composable
private fun PostListEmptyContent(
  lazyItemScope: Any,
  isInPopup: Boolean,
  isCatalogMode: Boolean
) {
  val text = when {
    isInPopup -> stringResource(R.string.post_list_no_posts_popup)
    isCatalogMode -> stringResource(R.string.post_list_no_catalog_selected)
    else -> stringResource(R.string.post_list_no_thread_selected)
  }

  val sizeModifier = if (isInPopup) {
    Modifier
      .fillMaxWidth()
      .height(180.dp)
  } else {
    when (lazyItemScope) {
      is LazyItemScope -> {
        with(lazyItemScope) {
          Modifier.fillParentMaxSize()
        }
      }
      is LazyGridItemScope -> {
        Modifier.fillMaxSize()
      }
      else -> {
        error("Unknown lazyItemScope: ${lazyItemScope::class.java.simpleName}")
      }
    }
  }

  Box(
    modifier = Modifier.then(sizeModifier),
    contentAlignment = Alignment.Center
  ) {
    KurobaComposeText(
      text = text
    )
  }
}

private fun LazyListScope.postListAsyncDataContent(
  postListAsync: AsyncData<PostsState>,
  emptyContent: @Composable (LazyItemScope, ) -> Unit,
  loadingContent: @Composable (LazyItemScope, ) -> Unit,
  errorContent: @Composable (LazyItemScope, AsyncData.Error) -> Unit,
  dataContent: (AsyncData.Data<PostsState>) -> Unit
) {
  when (postListAsync) {
    AsyncData.Uninitialized -> {
      // no-op
    }
    AsyncData.Loading -> {
      item(key = "loading_indicator") {
        loadingContent(this)
      }
    }
    is AsyncData.Error -> {
      item(key = "error_indicator") {
        errorContent(this, postListAsync)
      }
    }
    is AsyncData.Data -> {
      val posts by postListAsync.data.postsForUi
      if (posts.isEmpty()) {
        item(key = "empty_indicator") {
          emptyContent(this)
        }
      } else {
        dataContent(postListAsync)
      }
    }
  }
}

private fun LazyGridScope.postGridAsyncDataContent(
  postListAsync: AsyncData<PostsState>,
  emptyContent: @Composable (LazyGridItemScope) -> Unit,
  loadingContent: @Composable (LazyGridItemScope) -> Unit,
  errorContent: @Composable (LazyGridItemScope, AsyncData.Error) -> Unit,
  dataContent: (AsyncData.Data<PostsState>) -> Unit
) {
  when (postListAsync) {
    AsyncData.Uninitialized -> {
      // no-op
    }
    AsyncData.Loading -> {
      item(
        key = "loading_indicator",
        span = { GridItemSpan(maxLineSpan) }
      ) {
        loadingContent(this)
      }
    }
    is AsyncData.Error -> {
      item(
        key = "error_indicator",
        span = { GridItemSpan(maxLineSpan) }
      ) {
        errorContent(this, postListAsync)
      }
    }
    is AsyncData.Data -> {
      val posts by postListAsync.data.postsForUi
      if (posts.isEmpty()) {
        item(
          key = "empty_indicator",
          span = { GridItemSpan(maxLineSpan) }
        ) {
          emptyContent(this)
        }
      } else {
        dataContent(postListAsync)
      }
    }
  }
}

@Composable
private fun PostCellContainer(
  cellsPadding: PaddingValues,
  isCatalogMode: Boolean,
  textSelectionEnabled: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  isInPopup: Boolean,
  postCellData: PostCellData,
  postListOptions: PostListOptions,
  postListSelectionState: PostListSelectionState,
  postBlinkAnimationState: PostBlinkAnimationState,
  index: Int,
  totalCount: Int,
  animateInsertion: Boolean,
  animateUpdate: Boolean,
  lastViewedPostDescriptorForIndicator: PostDescriptor?,
  onPostBind: (PostCellData) -> Unit,
  onPostUnbind: (PostCellData) -> Unit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  onGoToPostClicked: ((PostCellData) -> Unit)?,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  var isInTextSelectionMode by remember { mutableStateOf(false) }
  val isInPostSelectionMode by postListSelectionState.isInSelectionMode

  PostCellContainerAnimated(
    animateInsertion = animateInsertion,
    animateUpdate = animateUpdate
  ) {
    Column(
      modifier = Modifier
        .kurobaClickable(
          enabled = !isInTextSelectionMode,
          onClick = {
            if (isInPostSelectionMode) {
              postListSelectionState.toggleSelection(postCellData.postDescriptor)
              return@kurobaClickable
            }

            if (isCatalogMode) {
              onPostCellClicked(postCellData)
            }
          },
          onLongClick = {
            if (isInPostSelectionMode) {
              postListSelectionState.toggleSelection(postCellData.postDescriptor)
              return@kurobaClickable
            }

            onPostCellLongClicked(postCellData)
          }
        )
    ) {
      PostCell(
        postViewMode = postListOptions.postViewMode,
        textSelectionEnabled = textSelectionEnabled,
        chanDescriptor = chanDescriptor,
        currentlyOpenedThread = currentlyOpenedThread,
        detectLinkableClicks = postListOptions.detectLinkableClicks,
        postCellCommentTextSizeSp = postListOptions.postCellCommentTextSizeSp,
        postCellSubjectTextSizeSp = postListOptions.postCellSubjectTextSizeSp,
        postCellData = postCellData,
        cellsPadding = cellsPadding,
        postBlinkAnimationState = postBlinkAnimationState,
        postListSelectionState = postListSelectionState,
        onTextSelectionModeChanged = { inSelectionMode -> isInTextSelectionMode = inSelectionMode },
        onPostBind = onPostBind,
        onPostUnbind = onPostUnbind,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onPostRepliesClicked = onPostRepliesClicked,
        onPostImageClicked = onPostImageClicked,
        onPostImageLongClicked = onPostImageLongClicked,
        onGoToPostClicked = onGoToPostClicked,
        reparsePostSubject = reparsePostSubject
      )

      val canDisplayLastViewedPostMarker = !isInPopup
        && !isCatalogMode
        && lastViewedPostDescriptorForIndicator == postCellData.postDescriptor
        && index < (totalCount - 1)

      val canDisplayPostDivider = !canDisplayLastViewedPostMarker
        && postListOptions.postViewMode == PostViewMode.List
        && index < (totalCount - 1)

      if (canDisplayLastViewedPostMarker) {
        Box(
          modifier = Modifier
            .height(4.dp)
            .fillMaxWidth()
            .drawBehind { drawRect(chanTheme.accentColor) }
        )
      }

      if (canDisplayPostDivider) {
        KurobaComposeDivider(
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun RestoreScrollPosition(
  firstPostDrawn: Boolean,
  chanDescriptor: ChanDescriptor,
  orientation: Int,
  postsScreenViewModelProvider: () -> PostScreenViewModel
) {
  var scrollPositionRestored by remember(key1 = orientation) { mutableStateOf(false) }
  if (scrollPositionRestored) {
    return
  }

  if (firstPostDrawn) {
    LaunchedEffect(
      key1 = chanDescriptor,
      key2 = orientation,
      block = {
        postsScreenViewModelProvider().restoreScrollPosition()
        scrollPositionRestored = true
      }
    )
  }
}

@Composable
private fun DisplayCatalogOrThreadNotSelectedPlaceholder(isCatalogMode: Boolean) {
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val additionalPaddings = remember(key1 = toolbarHeight) {
    PaddingValues(top = toolbarHeight)
  }

  KurobaComposeFadeIn {
    InsetsAwareBox(
      modifier = Modifier.fillMaxSize(),
      additionalPaddings = additionalPaddings,
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier.wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(id = R.string.empty_setup_feature),
          fontSize = 50.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        val text = if (isCatalogMode) {
          stringResource(id = R.string.empty_setup_no_catalog_selected)
        } else {
          stringResource(id = R.string.empty_setup_no_thread_selected)
        }

        KurobaComposeText(
          text = text,
          fontSize = 20.sp
        )
      }
    }
  }

}