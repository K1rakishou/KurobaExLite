package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeErrorWithButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import kotlinx.coroutines.delay


private val postCellKeyPrefix = "post_cell"

@Composable
internal fun PostListContent(
  modifier: Modifier = Modifier,
  postListOptions: PostListOptions,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellClicked: (PostCellData) -> Unit,
  onLinkableClicked: (PostCellData, PostCommentParser.TextPartSpan.Linkable) -> Unit,
  onPostRepliesClicked: (PostDescriptor) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListTouchingTopOrBottomStateChanged: (Boolean) -> Unit,
  onPostListDragStateChanged: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData) -> Unit
) {
  val postListAsync by postsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
  val chanDescriptorFromState by postsScreenViewModel.postScreenState.chanDescriptorFlow.collectAsState()
  val chanDescriptor = chanDescriptorFromState ?: return

  val orientation = LocalConfiguration.current.orientation
  val rememberedPosition = remember(key1 = chanDescriptor, key2 = orientation) {
    postsScreenViewModel.rememberedPosition(chanDescriptor, orientation)
  }
  val lazyListState = rememberLazyListState(
    initialFirstVisibleItemIndex = rememberedPosition.index,
    initialFirstVisibleItemScrollOffset = rememberedPosition.offset
  )

  if (postListAsync is AsyncData.Data) {
    val delta = 32

    LaunchedEffect(
      key1 = lazyListState.firstVisibleItemIndex,
      key2 = lazyListState.firstVisibleItemScrollOffset / delta,
      key3 = chanDescriptor,
      block = {
        // For debouncing purposes
        delay(16L)

        val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
        val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()

        if (firstVisibleItem != null && lastVisibleItem != null) {
          val firstVisibleItemIndex = firstVisibleItem.index
          val firstVisibleItemOffset = firstVisibleItem.offset
          val lastVisibleItemIndex = lastVisibleItem.index

          val totalCount = lazyListState.layoutInfo.totalItemsCount
          val touchingTop = firstVisibleItemIndex <= 0 && firstVisibleItemOffset in 0..delta
          val touchingBottom = lastVisibleItemIndex >= (totalCount - 1)

          onPostListTouchingTopOrBottomStateChanged(touchingTop || touchingBottom)
        }

        val postDataList = (postListAsync as? AsyncData.Data)?.data?.posts

        if (postsScreenViewModel is ThreadScreenViewModel && postDataList != null) {
          var firstCompletelyVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset >= 0 }
          if (firstCompletelyVisibleItem == null) {
            firstCompletelyVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
          }

          if (firstCompletelyVisibleItem != null) {
            val firstVisiblePostData = postDataList.getOrNull(firstCompletelyVisibleItem.index)?.value
            if (firstVisiblePostData != null) {
              postsScreenViewModel.onFirstVisiblePostScrollChanged(firstVisiblePostData)
            }
          }
        }
      })

    LaunchedEffect(
      lazyListState.firstVisibleItemIndex,
      lazyListState.firstVisibleItemScrollOffset,
      chanDescriptor,
      orientation,
      block = {
        if (lazyListState.firstVisibleItemIndex <= 0) {
          return@LaunchedEffect
        }

        // For debouncing purposes
        delay(50L)

        postsScreenViewModel.rememberPosition(
          chanDescriptor = chanDescriptor,
          index = lazyListState.firstVisibleItemIndex,
          offset = lazyListState.firstVisibleItemScrollOffset,
          orientation = orientation
        )
      })

    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        postsScreenViewModel.scrollRestorationEventFlow.collect { lastRememberedPosition ->
          lazyListState.scrollToItem(
            index = lastRememberedPosition.index,
            scrollOffset = lastRememberedPosition.offset
          )
        }
      })

    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        postsScreenViewModel.toolbarScrollEventFlow.collect { scrollDown ->
          val positionToScroll = if (scrollDown) {
            lazyListState.layoutInfo.totalItemsCount
          } else {
            0
          }

          lazyListState.scrollToItem(index = positionToScroll, scrollOffset = 0)
        }
      })
  }

  PostListInternal(
    modifier = modifier,
    chanDescriptor = chanDescriptor,
    lazyListState = lazyListState,
    postListOptions = postListOptions,
    postListAsync = postListAsync,
    postsScreenViewModel = postsScreenViewModel,
    onPostCellClicked = onPostCellClicked,
    onPostCellCommentClicked = remember {
      { postCellData: PostCellData, postComment: AnnotatedString, offset: Int ->
        processClickedAnnotation(
          postsScreenViewModel = postsScreenViewModel,
          postCellData = postCellData,
          postComment = postComment,
          characterOffset = offset,
          onLinkableClicked = onLinkableClicked
        )
      }
    },
    onPostRepliesClicked = remember {
      { postCellData: PostCellData ->
        onPostRepliesClicked(postCellData.postDescriptor)
      }
    },
    onThreadStatusCellClicked = remember {
      {
        postsScreenViewModel.resetTimer()
        postsScreenViewModel.refresh()
      }
    },
    onPostListScrolled = onPostListScrolled,
    onPostListDragStateChanged = onPostListDragStateChanged,
    onFastScrollerDragStateChanged = onFastScrollerDragStateChanged,
    onPostImageClicked = onPostImageClicked
  )

  // This piece of code waits until postListAsync is loaded (basically when it becomes AsyncData.Data)
  // and then also wait until at least PostCell is built and drawn in the LazyColumn then sets a
  // special flag in PostScreenViewModel which then triggers previous scroll position restoration.
  // We need to do all that because otherwise we won't scroll to the last position since the list
  // state might not have the necessary info for that.
  if (postListAsync is AsyncData.Data) {
    NotifyPostListBuiltIfNeeded(lazyListState, chanDescriptor, postsScreenViewModel)
  }
}

@Composable
private fun PostListInternal(
  modifier: Modifier,
  chanDescriptor: ChanDescriptor,
  lazyListState: LazyListState,
  postListOptions: PostListOptions,
  postListAsync: AsyncData<PostsState>,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListDragStateChanged: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit,
) {
  val isInPopup = postListOptions.isInPopup
  val pullToRefreshEnabled = postListOptions.pullToRefreshEnabled
  val isCatalogMode = postListOptions.isCatalogMode
  val cellsPadding = remember { PaddingValues(horizontal = 8.dp) }

  val lastViewedPostDescriptorForIndicator by postsScreenViewModel.postScreenState.lastViewedPostDescriptorForIndicator.collectAsState()
  val searchQuery by postsScreenViewModel.postScreenState.searchQueryFlow.collectAsState()
  val postsParsedOnce by postsScreenViewModel.postsFullyParsedOnceFlow.collectAsState()
  val currentlyOpenedThread by postsScreenViewModel.currentlyOpenedThreadFlow.collectAsState()

  val contentPadding = remember(key1 = searchQuery, key2 = postListOptions.contentPadding) {
    if (searchQuery.isNullOrEmpty()) {
      postListOptions.contentPadding
    } else {
      PaddingValues(
        start = postListOptions.contentPadding.calculateStartPadding(LayoutDirection.Ltr),
        end = postListOptions.contentPadding.calculateEndPadding(LayoutDirection.Ltr),
        top = postListOptions.contentPadding.calculateTopPadding() + searchInfoCellHeight,
        bottom = postListOptions.contentPadding.calculateBottomPadding(),
      )
    }
  }

  val pullToRefreshTopPaddingDp = remember(key1 = contentPadding) { contentPadding.calculateTopPadding() }
  val pullToRefreshState = rememberPullToRefreshState()

  val nestedScrollConnection = remember {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        onPostListScrolled(available.y)
        return Offset.Zero
      }
    }
  }

  val buildThreadStatusCellFunc: @Composable ((LazyItemScope) -> Unit)? = if (
    !isCatalogMode &&
    searchQuery == null &&
    postsScreenViewModel is ThreadScreenViewModel
  ) {
    { lazyItemScope: LazyItemScope ->
      with(lazyItemScope) {
        ThreadStatusCell(
          padding = cellsPadding,
          lazyListState = lazyListState,
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
        modifier = modifier.then(
          Modifier
            .nestedScroll(nestedScrollConnection)
            .pointerInput(
              key1 = Unit,
              block = {
                processDragEvents { dragging -> onPostListDragStateChanged(dragging) }
              }
            )
        ),
        lazyListState = lazyListState,
        contentPadding = contentPadding,
        onFastScrollerDragStateChanged = { dragging -> onFastScrollerDragStateChanged(dragging) },
        content = {
          postListAsyncDataContent(
            postListAsync = postListAsync,
            emptyContent = { PostListEmptyContent(isInPopup, isCatalogMode) },
            loadingContent = { PostListLoadingContent(isInPopup) },
            errorContent = { postListAsyncError -> PostListErrorContent(postListAsyncError, isInPopup, postsScreenViewModel) },
            dataContent = { postListAsyncData ->
              val abstractPostsState = postListAsyncData.data
              val previousPostDataInfoMap = abstractPostsState.postListAnimationInfoMap

              postList(
                isCatalogMode = isCatalogMode,
                isInPopup = isInPopup,
                chanDescriptor = chanDescriptor,
                currentlyOpenedThread = currentlyOpenedThread,
                cellsPadding = cellsPadding,
                postListOptions = postListOptions,
                postDataList = abstractPostsState.posts,
                lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
                onPostBind = { postCellData -> postsScreenViewModel.onPostBind(postCellData) },
                onPostUnbind = { postCellData -> postsScreenViewModel.onPostUnbind(postCellData) },
                canAnimateInsertion = { postCellData ->
                  return@postList canAnimateInsertion(
                    previousPostDataInfoMap = previousPostDataInfoMap,
                    postCellData = postCellData,
                    searchQuery = searchQuery,
                    inPopup = isInPopup,
                    postsParsedOnce = postsParsedOnce
                  )
                },
                canAnimateUpdate = { postCellData, rememberedHashForListAnimations ->
                  return@postList canAnimateUpdate(
                    previousPostDataInfoMap = previousPostDataInfoMap,
                    postCellData = postCellData,
                    searchQuery = searchQuery,
                    inPopup = isInPopup,
                    rememberedHashForListAnimations = rememberedHashForListAnimations,
                    postsParsedOnce = postsParsedOnce
                  )
                },
                onPostCellClicked = onPostCellClicked,
                onPostCellCommentClicked = onPostCellCommentClicked,
                onPostRepliesClicked = onPostRepliesClicked,
                onPostImageClicked = onPostImageClicked,
                reparsePostSubject = { postCellData -> postsScreenViewModel.reparsePostSubject(postCellData) },
                buildThreadStatusCell = buildThreadStatusCellFunc
              )
            }
          )
        }
      )

      if (searchQuery.isNotNullNorEmpty()) {
        SearchInfoCell(
          cellsPadding = cellsPadding,
          contentPadding = postListOptions.contentPadding,
          postsScreenViewModel = postsScreenViewModel,
          searchQuery = searchQuery
        )
      }
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
    onButtonClicked = { postsScreenViewModel.reload() }
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
    AsyncData.Empty -> {
      item(key = "empty_indicator") {
        emptyContent()
      }
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
      dataContent(postListAsync)
    }
  }
}

private fun LazyListScope.postList(
  isCatalogMode: Boolean,
  isInPopup: Boolean,
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  lastViewedPostDescriptorForIndicator: PostDescriptor?,
  cellsPadding: PaddingValues,
  postListOptions: PostListOptions,
  postDataList: List<State<PostCellData>>,
  onPostBind: (PostCellData) -> Unit,
  onPostUnbind: (PostCellData) -> Unit,
  canAnimateInsertion: (PostCellData) -> Boolean,
  canAnimateUpdate: (PostCellData, Murmur3Hash?) -> Boolean,
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData) -> Unit,
  reparsePostSubject: suspend (PostCellData) -> AnnotatedString?,
  buildThreadStatusCell: @Composable (LazyItemScope.() -> Unit)? = null
) {
  items(
    count = postDataList.size,
    key = { index -> "${postCellKeyPrefix}_${postDataList[index].value.postDescriptor}" },
    itemContent = { index ->
      val postCellData by postDataList[index]

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
        totalCount = postDataList.size,
        animateInsertion = animateInsertion,
        animateUpdate = animateUpdate,
        lastViewedPostDescriptorForIndicator = lastViewedPostDescriptorForIndicator,
        onPostBind = onPostBind,
        onPostUnbind = onPostUnbind,
        onPostCellClicked = onPostCellClicked,
        onPostCellCommentClicked = onPostCellCommentClicked,
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
  onPostCellClicked: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData) -> Unit,
  reparsePostSubject: suspend (PostCellData) -> AnnotatedString?
) {
  val chanTheme = LocalChanTheme.current

  val postCellCommentTextSizeSp = remember(postListOptions) { postListOptions.postCellCommentTextSizeSp }
  val postCellSubjectTextSizeSp = remember(postListOptions) { postListOptions.postCellSubjectTextSizeSp }
  val detectLinkableClicks = remember(postListOptions) { postListOptions.detectLinkableClicks }

  PostCellContainerAnimated(
    animateInsertion = animateInsertion,
    animateUpdate = animateUpdate,
    isCatalogMode = isCatalogMode,
    postCellData = postCellData,
    currentlyOpenedThread = currentlyOpenedThread
  ) {
    Column(
      modifier = Modifier
        .kurobaClickable(onClick = { onPostCellClicked(postCellData) })
        .padding(cellsPadding)
    ) {
      PostCell(
        isCatalogMode = isCatalogMode,
        chanDescriptor = chanDescriptor,
        detectLinkableClicks = detectLinkableClicks,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        postCellData = postCellData,
        onPostBind = onPostBind,
        onPostUnbind = onPostUnbind,
        onPostCellCommentClicked = onPostCellCommentClicked,
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
            .background(color = chanTheme.accentColorCompose)
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
private fun NotifyPostListBuiltIfNeeded(
  lazyListState: LazyListState,
  chanDescriptor: ChanDescriptor,
  postsScreenViewModel: PostScreenViewModel
) {
  var postListBuiltNotified by remember { mutableStateOf(false) }
  if (!postListBuiltNotified) {
    val firstPostDrawn = remember(key1 = lazyListState.layoutInfo) {
      val firstVisibleElement = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
        ?: return@remember false

      return@remember (firstVisibleElement.key as? String)
        ?.startsWith(postCellKeyPrefix)
        ?: false
    }

    if (firstPostDrawn) {
      LaunchedEffect(
        key1 = chanDescriptor,
        block = {
          postListBuiltNotified = true
          postsScreenViewModel.onPostListBuilt()
        }
      )
    }
  }
}