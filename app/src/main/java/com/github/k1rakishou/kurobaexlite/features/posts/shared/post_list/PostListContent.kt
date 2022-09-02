package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
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
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPartSpan
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.detectListScrollEvents
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import kotlinx.coroutines.delay


private const val postCellKeyPrefix = "post_cell"

@Composable
internal fun PostListContent(
  modifier: Modifier = Modifier,
  postListOptions: PostListOptions,
  postsScreenViewModelProvider: () -> PostScreenViewModel,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onLinkableClicked: (PostCellData, TextPartSpan.Linkable) -> Unit,
  onLinkableLongClicked: (PostCellData, TextPartSpan.Linkable) -> Unit,
  onPostRepliesClicked: (PostDescriptor) -> Unit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListTouchingTopOrBottomStateChanged: (Boolean) -> Unit,
  onCurrentlyTouchingPostList: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  emptyContent: @Composable LazyItemScope.(Boolean, Boolean) -> Unit = { isInPopup, isCatalogMode ->
    PostListEmptyContent(isInPopup, isCatalogMode)
  },
  loadingContent: @Composable LazyItemScope.(Boolean) -> Unit = { isInPopup ->
    PostListLoadingContent(isInPopup)
  },
  errorContent: @Composable LazyItemScope.(AsyncData.Error, Boolean) -> Unit = { postListAsyncError, isInPopup ->
    PostListErrorContent(postListAsyncError, isInPopup, postsScreenViewModelProvider())
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

  val openedFromScreenKey = postListOptions.ownerScreenKey
  val postListAsyncMut by postsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
  val postListAsync = postListAsyncMut
  val postListAsyncUpdated by rememberUpdatedState(newValue = postListAsync)

  val lazyListState = rememberLazyListState()

  fun processPostListScrollEventFunc() {
    processPostListScrollEvent(
      postsScreenViewModel = postsScreenViewModel,
      postListAsync = postListAsyncUpdated,
      lazyListState = lazyListState,
      chanDescriptor = chanDescriptor,
      orientation = postListOptions.orientation,
      onPostListTouchingTopOrBottomStateChanged = onPostListTouchingTopOrBottomStateChanged
    )
  }

  if (postListAsync is AsyncData.Data) {
    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        postsScreenViewModel.scrollRestorationEventFlow.collect { lastRememberedPosition ->
          lazyListState.scrollToItem(
            index = lastRememberedPosition.index,
            scrollOffset = lastRememberedPosition.offset
          )

          processPostListScrollEventFunc()
        }
      })

    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        postsScreenViewModel.postListScrollEventFlow.collect { toolbarScrollEvent ->
          val positionToScroll = when (toolbarScrollEvent) {
            PostScreenViewModel.ToolbarScrollEvent.ScrollBottom -> {
              lazyListState.layoutInfo.totalItemsCount
            }
            PostScreenViewModel.ToolbarScrollEvent.ScrollTop -> {
              0
            }
            is PostScreenViewModel.ToolbarScrollEvent.ScrollToItem -> {
              val postState = postListAsync.data
              postState.postIndexByPostDescriptor(toolbarScrollEvent.postDescriptor)
            }
          }

          if (positionToScroll == null) {
            return@collect
          }

          lazyListState.scrollToItem(
            index = positionToScroll,
            scrollOffset = 0
          )

          processPostListScrollEventFunc()
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
            lazyListState.scrollToItem(
              index = indexToScroll,
              scrollOffset = 0
            )

            processPostListScrollEventFunc()
          }
        }
      }
    )
  }

  PostListInternal(
    modifier = modifier,
    chanDescriptor = chanDescriptor,
    lazyListState = lazyListState,
    postListOptions = postListOptions,
    postListAsync = postListAsync,
    postsScreenViewModelProvider = postsScreenViewModelProvider,
    onPostCellClicked = onPostCellClicked,
    onPostCellLongClicked = onPostCellLongClicked,
    onPostCellCommentClicked = { postCellData: PostCellData, postComment: AnnotatedString, offset: Int ->
      processClickedAnnotation(
        postsScreenViewModel = postsScreenViewModel,
        postCellData = postCellData,
        postComment = postComment,
        characterOffset = offset,
        longClicked = false,
        onLinkableClicked = onLinkableClicked,
        onLinkableLongClicked = onLinkableLongClicked
      )
    },
    onPostCellCommentLongClicked = { postCellData: PostCellData, postComment: AnnotatedString, offset: Int ->
      processClickedAnnotation(
        postsScreenViewModel = postsScreenViewModel,
        postCellData = postCellData,
        postComment = postComment,
        characterOffset = offset,
        longClicked = true,
        onLinkableClicked = onLinkableClicked,
        onLinkableLongClicked = onLinkableLongClicked
      )
    },
    onPostRepliesClicked = { postCellData: PostCellData ->
      onPostRepliesClicked(postCellData.postDescriptor)
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
    emptyContent = emptyContent,
    loadingContent = loadingContent,
    errorContent = errorContent,
  )

  if (postListAsync is AsyncData.Data) {
    // Call processPostListScrollEventFunc() as soon as we get the actual post data so that we can
    // reset the last seen post indicator in threads with few posts that fit the whole screen without
    // having to scroll it. Otherwise the indicator will stay (since we can't scroll the thread and
    // we only ever update it on scroll events).
    LaunchedEffect(
      key1 = postListAsync.data.chanDescriptor,
      block = {
        processPostListScrollEventFunc()
      }
    )

    RestoreScrollPosition(
      lazyListState = lazyListState,
      chanDescriptor = chanDescriptor,
      orientation = postListOptions.orientation,
      postsScreenViewModel = postsScreenViewModel
    )
  }
}

private fun processPostListScrollEvent(
  postsScreenViewModel: PostScreenViewModel,
  postListAsync: AsyncData<PostsState>,
  lazyListState: LazyListState,
  chanDescriptor: ChanDescriptor,
  orientation: Int,
  onPostListTouchingTopOrBottomStateChanged: (Boolean) -> Unit
) {
  if (postListAsync !is AsyncData.Data) {
    return
  }

  val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
  val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
  val maxAllowedOffset = 64

  if (firstVisibleItem != null && lastVisibleItem != null) {
    val firstVisibleItemIndex = firstVisibleItem.index
    val firstVisibleItemOffset = firstVisibleItem.offset
    val lastVisibleItemIndex = lastVisibleItem.index

    val totalCount = lazyListState.layoutInfo.totalItemsCount
    val touchingTop = firstVisibleItemIndex <= 0 && firstVisibleItemOffset in 0..maxAllowedOffset
    val touchingBottom = lastVisibleItemIndex >= (totalCount - 1)

    onPostListTouchingTopOrBottomStateChanged(touchingTop || touchingBottom)
  }

  val postDataList = (postListAsync as? AsyncData.Data)?.data?.posts
  val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo

  if (postDataList != null) {
    var firstCompletelyVisibleItem = visibleItemsInfo.firstOrNull { it.offset >= 0 }
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

  postsScreenViewModel.rememberPosition(
    chanDescriptor = chanDescriptor,
    index = lazyListState.firstVisibleItemIndex,
    offset = lazyListState.firstVisibleItemScrollOffset,
    orientation = orientation
  )
}

@Composable
private fun PostListInternal(
  modifier: Modifier,
  chanDescriptor: ChanDescriptor,
  lazyListState: LazyListState,
  postListOptions: PostListOptions,
  postListAsync: AsyncData<PostsState>,
  postsScreenViewModelProvider: () -> PostScreenViewModel,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onCurrentlyTouchingPostList: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
  emptyContent: @Composable LazyItemScope.(Boolean, Boolean) -> Unit,
  loadingContent: @Composable LazyItemScope.(Boolean) -> Unit,
  errorContent: @Composable LazyItemScope.(AsyncData.Error, Boolean) -> Unit,
) {
  val isInPopup = postListOptions.isInPopup
  val pullToRefreshEnabled = postListOptions.pullToRefreshEnabled
  val isCatalogMode = postListOptions.isCatalogMode
  val cellsPadding = remember { PaddingValues(horizontal = 8.dp) }

  val postsScreenViewModel = postsScreenViewModelProvider()
  val lastViewedPostDescriptorForIndicator by postsScreenViewModel.postScreenState.lastViewedPostForIndicator.collectAsState()
  val currentlyOpenedThread by postsScreenViewModel.currentlyOpenedThreadFlow.collectAsState()
  val searchQuery by postsScreenViewModel.postScreenState.searchQueryFlow.collectAsState()
  val fastScrollerMarks by postsScreenViewModel.fastScrollerMarksFlow.collectAsState()

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

  val buildThreadStatusCellFunc: @Composable ((LazyItemScope) -> Unit)? = if (
    !isCatalogMode &&
    searchQuery == null &&
    postsScreenViewModel is ThreadScreenViewModel
  ) {
    { lazyItemScope: LazyItemScope ->
      with(lazyItemScope) {
        ThreadStatusCell(
          padding = cellsPadding,
          threadScreenViewModel = postsScreenViewModel,
          onThreadStatusCellClicked = onThreadStatusCellClicked
        )
      }
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
      onTriggered = remember {
        {
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
      }
    ) {
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
        lazyListState = lazyListState,
        contentPadding = contentPadding,
        fastScrollerMarks = fastScrollerMarks,
        onFastScrollerDragStateChanged = { dragging -> onFastScrollerDragStateChanged(dragging) },
        content = {
          postListAsyncDataContent(
            postListAsync = postListAsync,
            emptyContent = { emptyContent(isInPopup, isCatalogMode) },
            loadingContent = { loadingContent(isInPopup) },
            errorContent = { postListAsyncError -> errorContent(postListAsyncError, isInPopup) },
            dataContent = { postListAsyncData ->
              val abstractPostsState = postListAsyncData.data
              val previousPostDataInfoMap = abstractPostsState.postListAnimationInfoMap
              val postCellDataList = abstractPostsState.posts

              postList(
                isCatalogMode = isCatalogMode,
                isInPopup = isInPopup,
                chanDescriptor = chanDescriptor,
                currentlyOpenedThread = currentlyOpenedThread,
                cellsPadding = cellsPadding,
                postListOptions = postListOptions,
                postCellDataList = postCellDataList,
                lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
                onPostBind = { postCellData -> postsScreenViewModel.onPostBind(postCellData) },
                onPostUnbind = { postCellData -> postsScreenViewModel.onPostUnbind(postCellData) },
                onCopySelectedText = onCopySelectedText,
                onQuoteSelectedText = onQuoteSelectedText,
                canAnimateInsertion = { postCellData ->
                  return@postList canAnimateInsertion(
                    previousPostDataInfoMap = previousPostDataInfoMap,
                    postCellData = postCellData,
                    searchQuery = postsScreenViewModel.postScreenState.currentSearchQuery,
                    inPopup = isInPopup,
                    postsParsedOnce = postsScreenViewModel.postsFullyParsedOnceFlow.value
                  )
                },
                canAnimateUpdate = { postCellData, rememberedHashForListAnimations ->
                  return@postList canAnimateUpdate(
                    previousPostDataInfoMap = previousPostDataInfoMap,
                    postCellData = postCellData,
                    searchQuery = postsScreenViewModel.postScreenState.currentSearchQuery,
                    inPopup = isInPopup,
                    rememberedHashForListAnimations = rememberedHashForListAnimations,
                    postsParsedOnce = postsScreenViewModel.postsFullyParsedOnceFlow.value
                  )
                },
                onPostCellClicked = onPostCellClicked,
                onPostCellLongClicked = onPostCellLongClicked,
                onPostCellCommentClicked = onPostCellCommentClicked,
                onPostCellCommentLongClicked = onPostCellCommentLongClicked,
                onPostRepliesClicked = onPostRepliesClicked,
                onPostImageClicked = onPostImageClicked,
                reparsePostSubject = { postCellData, onPostSubjectParsed ->
                  postsScreenViewModel.reparsePostSubject(
                    postCellData = postCellData,
                    onPostSubjectParsed = onPostSubjectParsed
                  )
                },
                buildThreadStatusCell = buildThreadStatusCellFunc
              )
            }
          )
        }
      )
    }
  }
}

@Composable
private fun LazyItemScope.PostListErrorContent(
  postListAsyncError: AsyncData.Error,
  isInPopup: Boolean,
  postsScreenViewModel: PostScreenViewModel
) {
  val errorMessage = remember(key1 = postListAsyncError) {
    postListAsyncError.error.errorMessageOrClassName()
  }

  val sizeModifier = if (isInPopup) {
    Modifier
      .fillMaxWidth()
      .height(180.dp)
  } else {
    Modifier.fillParentMaxSize()
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
private fun LazyItemScope.PostListLoadingContent(isInPopup: Boolean) {
  val sizeModifier = if (isInPopup) {
    Modifier
      .fillMaxWidth()
      .height(180.dp)
  } else {
    Modifier.fillParentMaxSize()
  }

  KurobaComposeLoadingIndicator(
    modifier = Modifier
      .then(sizeModifier)
      .padding(8.dp)
  )
}

@Composable
private fun LazyItemScope.PostListEmptyContent(
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
    Modifier.fillParentMaxSize()
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
  emptyContent: @Composable LazyItemScope.() -> Unit,
  loadingContent: @Composable LazyItemScope.() -> Unit,
  errorContent: @Composable LazyItemScope.(AsyncData.Error) -> Unit,
  dataContent: (AsyncData.Data<PostsState>) -> Unit
) {
  when (postListAsync) {
    AsyncData.Uninitialized -> {
      // no-op
    }
    AsyncData.Loading -> {
      item(key = "loading_indicator") {
        loadingContent()
      }
    }
    is AsyncData.Error -> {
      item(key = "error_indicator") {
        errorContent(postListAsync)
      }
    }
    is AsyncData.Data -> {
      val posts = postListAsync.data.posts

      if (posts.isEmpty()) {
        item(key = "empty_indicator") {
          emptyContent()
        }
      } else {
        dataContent(postListAsync)
      }
    }
  }
}

@Stable
private fun LazyListScope.postList(
  isCatalogMode: Boolean,
  isInPopup: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  lastViewedPostDescriptorForIndicator: PostDescriptor?,
  cellsPadding: PaddingValues,
  postListOptions: PostListOptions,
  postCellDataList: List<PostCellData>,
  onPostBind: (PostCellData) -> Unit,
  onPostUnbind: (PostCellData) -> Unit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  canAnimateInsertion: (PostCellData) -> Boolean,
  canAnimateUpdate: (PostCellData, Murmur3Hash?) -> Boolean,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellLongClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
  buildThreadStatusCell: @Composable (LazyItemScope.() -> Unit)? = null
) {
  items(
    count = postCellDataList.size,
    key = { index -> "${postCellKeyPrefix}_${postCellDataList[index].postDescriptor}" },
    itemContent = { index ->
      val postCellData = postCellDataList[index]

      var rememberedHashForUpdateAnimation by remember {
        mutableStateOf(postCellData.postServerDataHashForListAnimations)
      }
      val animateInsertion = remember(key1 = postCellData.postDescriptor) {
        canAnimateInsertion(postCellData)
      }
      val animateUpdate = remember(
        key1 = postCellData.postDescriptor,
        key2 = rememberedHashForUpdateAnimation,
        key3 = postCellData.postServerDataHashForListAnimations
      ) {
        canAnimateUpdate(postCellData, rememberedHashForUpdateAnimation)
      }

      PostCellContainer(
        cellsPadding = cellsPadding,
        isCatalogMode = isCatalogMode,
        chanDescriptor = chanDescriptor,
        currentlyOpenedThread = currentlyOpenedThread,
        isInPopup = isInPopup,
        postCellData = postCellData,
        postListOptions = postListOptions,
        index = index,
        totalCount = postCellDataList.size,
        animateInsertion = animateInsertion,
        animateUpdate = animateUpdate,
        lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
        onPostBind = onPostBind,
        onPostUnbind = onPostUnbind,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        onPostCellClicked = onPostCellClicked,
        onPostCellLongClicked = onPostCellLongClicked,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onPostRepliesClicked = onPostRepliesClicked,
        onPostImageClicked = onPostImageClicked,
        reparsePostSubject = reparsePostSubject
      )

      SideEffect { rememberedHashForUpdateAnimation = postCellData.postServerDataHashForListAnimations }
    }
  )

  if (buildThreadStatusCell != null) {
    item(key = threadStatusCellKey) {
      buildThreadStatusCell()
    }
  }
}

@Composable
private fun LazyItemScope.PostCellContainer(
  cellsPadding: PaddingValues,
  isCatalogMode: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  isInPopup: Boolean,
  postCellData: PostCellData,
  postListOptions: PostListOptions,
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
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
) {
  val chanTheme = LocalChanTheme.current

  val postCellCommentTextSizeSp = remember(postListOptions) { postListOptions.postCellCommentTextSizeSp }
  val postCellSubjectTextSizeSp = remember(postListOptions) { postListOptions.postCellSubjectTextSizeSp }
  val detectLinkableClicks = remember(postListOptions) { postListOptions.detectLinkableClicks }

  var isInSelectionMode by remember { mutableStateOf(false) }

  PostCellContainerAnimated(
    animateInsertion = animateInsertion,
    animateUpdate = animateUpdate,
    isCatalogMode = isCatalogMode,
    postCellData = postCellData,
    currentlyOpenedThread = currentlyOpenedThread
  ) {
    Column(
      modifier = Modifier
        .kurobaClickable(
          enabled = !isInSelectionMode,
          onClick = {
            if (isCatalogMode) {
              onPostCellClicked(postCellData)
            }
          },
          onLongClick = {
            onPostCellLongClicked(postCellData)
          }
        )
        .padding(cellsPadding)
    ) {
      PostCell(
        isCatalogMode = isCatalogMode,
        chanDescriptor = chanDescriptor,
        detectLinkableClicks = detectLinkableClicks,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        postCellData = postCellData,
        onTextSelectionModeChanged = { inSelectionMode -> isInSelectionMode = inSelectionMode },
        onPostBind = onPostBind,
        onPostUnbind = onPostUnbind,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onPostRepliesClicked = onPostRepliesClicked,
        onPostImageClicked = onPostImageClicked,
        reparsePostSubject = reparsePostSubject
      )

      val canDisplayLastViewedPostMarker = !isInPopup
        && !isCatalogMode
        && lastViewedPostDescriptorForIndicator == postCellData.postDescriptor
        && index < (totalCount - 1)

      if (canDisplayLastViewedPostMarker) {
        Box(
          modifier = Modifier
            .height(4.dp)
            .fillMaxWidth()
            .background(color = chanTheme.accentColor)
        )
      } else if (index < (totalCount - 1)) {
        KurobaComposeDivider(
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun RestoreScrollPosition(
  lazyListState: LazyListState,
  chanDescriptor: ChanDescriptor,
  orientation: Int,
  postsScreenViewModel: PostScreenViewModel
) {
  var scrollPositionRestored by remember(key1 = orientation) { mutableStateOf(false) }
  if (scrollPositionRestored) {
    return
  }

  val layoutInfo by remember(key1 = lazyListState) { derivedStateOf { lazyListState.layoutInfo } }

  val firstPostDrawn = remember(key1 = layoutInfo, key2 = orientation) {
    val firstVisibleElement = layoutInfo.visibleItemsInfo.firstOrNull()
      ?: return@remember false

    return@remember (firstVisibleElement.key as? String)
      ?.startsWith(postCellKeyPrefix)
      ?: false
  }

  if (firstPostDrawn) {
    LaunchedEffect(
      key1 = chanDescriptor,
      key2 = orientation,
      block = {
        postsScreenViewModel.restoreScrollPosition()
        scrollPositionRestored = true
      }
    )
  }
}

@Composable
private fun DisplayCatalogOrThreadNotSelectedPlaceholder(isCatalogMode: Boolean) {
  var canRender by remember { mutableStateOf(false) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      delay(250L)
      canRender = true
    })

  if (!canRender) {
    return
  }

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val additionalPaddings = remember(key1 = toolbarHeight) {
    PaddingValues(top = toolbarHeight)
  }

  val alphaAnimatable = remember { Animatable(initialValue = 0f) }
  val alphaAnimation by alphaAnimatable.asState()

  LaunchedEffect(
    key1 = Unit,
    block = { alphaAnimatable.animateTo(1f, animationSpec = tween(500)) }
  )

  InsetsAwareBox(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer { this.alpha = alphaAnimation },
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