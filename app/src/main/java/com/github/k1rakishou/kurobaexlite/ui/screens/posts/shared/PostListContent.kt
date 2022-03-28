package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared

import android.os.SystemClock
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.extractLinkableAnnotationItem
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.local.SpoilerPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeErrorWithButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.logcat

private val animationTranslationDelta = 100.dp
private val insertAnimationTotalDurationMs = 200
private val updateAnimationTotalDurationMs = 800
private val searchInfoCellHeight = 32.dp
private val postCellKeyPrefix = "post_cell"
private val threadStatusCellKey = "thread_status_cell"

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
      { threadDescriptor: ThreadDescriptor ->
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
}

private fun processClickedAnnotation(
  postsScreenViewModel: PostScreenViewModel,
  postCellData: PostCellData,
  postComment: AnnotatedString,
  characterOffset: Int,
  onLinkableClicked: (PostCellData, PostCommentParser.TextPartSpan.Linkable) -> Unit,
) {
  val parsedPostDataContext = postCellData.parsedPostData?.parsedPostDataContext
    ?: return
  val offset = findFirstNonNewLineCharReversed(characterOffset, postComment)
    ?: return

  val clickedAnnotations = postComment.getStringAnnotations(offset, offset)

  for (clickedAnnotation in clickedAnnotations) {
    when (clickedAnnotation.tag) {
      PostCommentApplier.ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG -> {
        if (!parsedPostDataContext.revealFullPostComment) {
          postsScreenViewModel.reparsePost(
            postCellData = postCellData,
            parsedPostDataContext = parsedPostDataContext.copy(revealFullPostComment = true)
          )
        }

        break
      }
      PostCommentApplier.ANNOTATION_POST_LINKABLE -> {
        val text = postComment.text.substring(clickedAnnotation.start, clickedAnnotation.end)
        val linkable = clickedAnnotation.extractLinkableAnnotationItem()
        logcat(tag = "processClickedAnnotation") {
          "Clicked '${text}' with linkable: ${linkable}"
        }

        if (linkable != null) {
          onLinkableClicked(postCellData, linkable)
        }

        break
      }
      PostCommentApplier.ANNOTATION_POST_SPOILER_TEXT -> {
        logcat(tag = "processClickedAnnotation") {
          "Clicked spoiler text, start=${clickedAnnotation.start}, end=${clickedAnnotation.end}"
        }

        val textSpoilerOpenedPositionSet = parsedPostDataContext.textSpoilerOpenedPositionSet.toMutableSet()
        val spoilerPosition = SpoilerPosition(clickedAnnotation.start, clickedAnnotation.end)

        if (textSpoilerOpenedPositionSet.contains(spoilerPosition)) {
          textSpoilerOpenedPositionSet.remove(spoilerPosition)
        } else {
          textSpoilerOpenedPositionSet.add(spoilerPosition)
        }

        postsScreenViewModel.reparsePost(
          postCellData = postCellData,
          parsedPostDataContext = parsedPostDataContext
            .copy(textSpoilerOpenedPositionSet = textSpoilerOpenedPositionSet)
        )
      }
    }
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

  val previousPostDataInfoMap = remember(
    key1 = postsScreenViewModel.chanDescriptor,
    key2 = isInPopup,
    key3 = postListAsync
  ) {
    if (isInPopup || postListAsync !is AsyncData.Data) {
      return@remember null
    }

    val abstractPostsState = (postListAsync as? AsyncData.Data)?.data
      ?: return@remember null

    val postDataList = abstractPostsState.posts

    // Pre-insert first batch of posts into the previousPostDataInfoMap so that we don't play
    // animations for recently opened catalogs/threads. We are doing this right inside of the
    // composition because otherwise there is some kind of a delay before LaunchedEffect is executed
    // so the first posts are always animated.

    val previousPosts = mutableMapOf<PostDescriptor, PreviousPostDataInfo>()

    postDataList.forEach { postDataState ->
      postDataState.value.postServerDataHashForListAnimations?.let { hash ->
        previousPosts[postDataState.value.postDescriptor] = PreviousPostDataInfo(hash)
      }
    }

    return@remember previousPosts
  }

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
                  canAnimateInsertion(
                    previousPostDataInfoMap = previousPostDataInfoMap,
                    postCellData = postCellData,
                    searchQuery = searchQuery,
                    postsParsedOnce = postsParsedOnce
                  )
                },
                canAnimateUpdate = { postCellData ->
                  canAnimateUpdate(
                    previousPostDataInfoMap = previousPostDataInfoMap,
                    postCellData = postCellData,
                    searchQuery = searchQuery,
                    lastUpdatedOn = abstractPostsState.lastUpdatedOn,
                    postsParsedOnce = postsParsedOnce
                  )
                },
                onPostCellAnimatedContainerBuilt = { postCellData ->
                  val hash = postCellData.postServerDataHashForListAnimations

                  if (previousPostDataInfoMap != null && hash != null) {
                    // Add each post into the previousPostDataInfoMap so that we don't run animations more than
                    // once for each post.
                    val previousPostDataInfo = previousPostDataInfoMap[postCellData.postDescriptor]
                    if (previousPostDataInfo == null) {
                      previousPostDataInfoMap[postCellData.postDescriptor] = PreviousPostDataInfo(hash)
                    } else if (previousPostDataInfo.hash != hash) {
                      previousPostDataInfoMap[postCellData.postDescriptor]!!.hash = hash
                    }
                  }
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
  canAnimateUpdate: (PostCellData) -> Boolean,
  onPostCellAnimatedContainerBuilt: (PostCellData) -> Unit,
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

      val animateInsertion = remember(postCellData) { canAnimateInsertion(postCellData) }
      val animateUpdate = remember(postCellData) { canAnimateUpdate(postCellData) }

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

      SideEffect { onPostCellAnimatedContainerBuilt(postCellData) }
    }
  )

  if (buildThreadStatusCell != null) {
    item(key = threadStatusCellKey) {
      buildThreadStatusCell()
    }
  }
}

private fun canAnimateUpdate(
  previousPostDataInfoMap: MutableMap<PostDescriptor, PreviousPostDataInfo>?,
  postCellData: PostCellData,
  searchQuery: String?,
  lastUpdatedOn: Long,
  postsParsedOnce: Boolean
): Boolean {
  if (previousPostDataInfoMap == null || searchQuery != null || !postsParsedOnce) {
    return false
  }

  val previousPostDataInfo = previousPostDataInfoMap[postCellData.postDescriptor]
  if (previousPostDataInfo == null) {
    return false
  }

  if (previousPostDataInfo.hash == postCellData.postServerDataHashForListAnimations) {
    return false
  }

  return lastUpdatedOn + updateAnimationTotalDurationMs >= SystemClock.elapsedRealtime()
}

private fun canAnimateInsertion(
  previousPostDataInfoMap: MutableMap<PostDescriptor, PreviousPostDataInfo>?,
  postCellData: PostCellData,
  searchQuery: String?,
  postsParsedOnce: Boolean
): Boolean {
  if (previousPostDataInfoMap == null || searchQuery != null || !postsParsedOnce) {
    return false
  }

  val previousPostDataInfo = previousPostDataInfoMap[postCellData.postDescriptor]
  if (previousPostDataInfo == null) {
    return true
  }

  return false
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.SearchInfoCell(
  cellsPadding: PaddingValues,
  contentPadding: PaddingValues,
  postsScreenViewModel: PostScreenViewModel,
  searchQuery: String?
) {
  if (searchQuery == null) {
    return
  }

  val chanTheme = LocalChanTheme.current
  val foundPostsCount = postsScreenViewModel.postScreenState.displayingPostsCount ?: 0

  val combinedPaddings = remember(key1 = cellsPadding) {
    PaddingValues(
      start = cellsPadding.calculateStartPadding(LayoutDirection.Ltr),
      end = cellsPadding.calculateEndPadding(LayoutDirection.Ltr),
      top = 4.dp
    )
  }

  val topOffset = remember(key1 = contentPadding) {
    contentPadding.calculateTopPadding()
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(searchInfoCellHeight)
      .padding(combinedPaddings)
      .align(Alignment.TopCenter)
      .offset(y = topOffset)
  ) {
    val context = LocalContext.current

    KurobaComposeCardView(
      backgroundColor = chanTheme.backColorSecondaryCompose
    ) {
      val text = remember(key1 = foundPostsCount, key2 = searchQuery) {
        context.resources.getString(
          R.string.search_hint,
          foundPostsCount,
          context.resources.getQuantityString(R.plurals.posts, foundPostsCount),
          searchQuery
        )
      }

      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp),
        text = text,
        color = chanTheme.textColorSecondaryCompose,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

  }

}

@Composable
private fun LazyItemScope.ThreadStatusCell(
  padding: PaddingValues,
  lazyListState: LazyListState,
  threadScreenViewModel: ThreadScreenViewModel,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val threadStatusCellDataFromState by threadScreenViewModel.postScreenState.threadCellDataState.collectAsState()
  val chanDescriptor = threadScreenViewModel.postScreenState.chanDescriptor
  val threadStatusCellData = threadStatusCellDataFromState

  if (threadStatusCellData == null || (chanDescriptor == null || chanDescriptor !is ThreadDescriptor)) {
    Spacer(modifier = Modifier.height(Dp.Hairline))
    return
  }

  val fabSize = dimensionResource(id = R.dimen.fab_size)
  val fabEndOffset = dimensionResource(id = R.dimen.post_list_fab_end_offset)

  val coroutineScope = rememberCoroutineScope()
  val lastItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index

  DisposableEffect(
    key1 = lastItemIndex,
    effect = {
      val job = coroutineScope.launch {
        delay(125L)

        val threadStatusCellItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.key == threadStatusCellKey
        if (threadStatusCellItem) {
          threadScreenViewModel.onPostListTouchingBottom()
        }
      }

      onDispose {
        job.cancel()

        val threadStatusCellItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.key == threadStatusCellKey
        if (!threadStatusCellItem) {
          threadScreenViewModel.onPostListNotTouchingBottom()
        }
      }
    })

  var timeUntilNextUpdateSeconds by remember { mutableStateOf(0L) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      while (isActive) {
        delay(1000L)
        timeUntilNextUpdateSeconds = threadScreenViewModel.timeUntilNextUpdateMs / 1000L
      }
    })

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .kurobaClickable(onClick = { onThreadStatusCellClicked(chanDescriptor) })
  ) {
    val context = LocalContext.current

    val threadStatusCellText = remember(key1 = threadStatusCellData, key2 = timeUntilNextUpdateSeconds) {
      buildAnnotatedString {
        if (threadStatusCellData.totalReplies > 0) {
          append(threadStatusCellData.totalReplies.toString())
          append("R")
        }

        if (threadStatusCellData.totalImages > 0) {
          if (length > 0) {
            append(", ")
          }

          append(threadStatusCellData.totalImages.toString())
          append("I")
        }

        if (threadStatusCellData.totalPosters > 0) {
          if (length > 0) {
            append(", ")
          }

          append(threadStatusCellData.totalPosters.toString())
          append("P")
        }

        append("\n")

        if (threadStatusCellData.lastLoadError == null) {
          val loadingText = if (timeUntilNextUpdateSeconds > 0L) {
            context.resources.getString(
              R.string.thread_screen_status_cell_loading_in,
              timeUntilNextUpdateSeconds
            )
          } else {
            context.resources.getString(R.string.thread_screen_status_cell_loading_right_now)
          }

          append(loadingText)
        } else {
          val lastLoadErrorText = threadStatusCellData.errorMessage(context)

          append(lastLoadErrorText)
          append("\n")
          append(context.resources.getString(R.string.thread_load_failed_tap_to_refresh))
        }
      }
    }

    val combinedPaddings = remember(key1 = threadStatusCellData.lastLoadError) {
      val endPadding = if (threadStatusCellData.lastLoadError != null) {
        fabSize + fabEndOffset
      } else {
        0.dp
      }

      PaddingValues(
        start = padding.calculateStartPadding(LayoutDirection.Ltr),
        end = padding.calculateEndPadding(LayoutDirection.Ltr) + endPadding,
        top = 16.dp,
        bottom = 16.dp
      )
    }

    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(combinedPaddings)
        .align(Alignment.Center),
      text = threadStatusCellText,
      color = chanTheme.textColorSecondaryCompose,
      textAlign = TextAlign.Center
    )
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
private fun PostCellContainerAnimated(
  animateInsertion: Boolean,
  animateUpdate: Boolean,
  isCatalogMode: Boolean,
  postCellData: PostCellData,
  currentlyOpenedThread: ThreadDescriptor?,
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  var currentAnimation by remember { mutableStateOf<AnimationType?>(null) }
  val contentMovable = remember { movableContentOf { content() } }

  if (animateInsertion || animateUpdate || currentAnimation != null) {
    if (animateInsertion || currentAnimation == AnimationType.Insertion) {
      SideEffect { currentAnimation = AnimationType.Insertion }

      PostCellContainerInsertAnimation(
        onAnimationFinished = { currentAnimation = null },
        content = contentMovable
      )

      return
    }

    if (animateUpdate || currentAnimation == AnimationType.Update) {
      SideEffect { currentAnimation = AnimationType.Update }

      PostCellContainerUpdateAnimation(
        onAnimationFinished = { currentAnimation = null },
        content = contentMovable
      )

      return
    }
  }

  val bgColor = remember(key1 = isCatalogMode, key2 = currentlyOpenedThread) {
    if (isCatalogMode && currentlyOpenedThread == postCellData.postDescriptor.threadDescriptor) {
      chanTheme.highlighterColorCompose.copy(alpha = 0.3f)
    } else {
      Color.Unspecified
    }
  }

  Box(modifier = Modifier.background(bgColor)) {
    contentMovable()
  }
}

enum class AnimationType {
  Insertion,
  Update
}

@Composable
private fun PostCellContainerUpdateAnimation(
  onAnimationFinished: () -> Unit,
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val bgColorAnimatable = remember { Animatable(Color.Unspecified) }

  val startColor = chanTheme.backColorCompose
  val endColor = chanTheme.selectedOnBackColorCompose

  LaunchedEffect(
    key1 = Unit,
    block = {
      try {
        bgColorAnimatable.snapTo(startColor)
        bgColorAnimatable.animateTo(endColor, tween(durationMillis = 400, easing = LinearEasing))
        bgColorAnimatable.animateTo(startColor, tween(durationMillis = 400, easing = LinearEasing))
      } finally {
        bgColorAnimatable.snapTo(Color.Unspecified)
        onAnimationFinished()
      }
    })

  val bgColor by bgColorAnimatable.asState()

  Box(modifier = Modifier.background(bgColor)) {
    content()
  }
}

@Composable
private fun PostCellContainerInsertAnimation(
  onAnimationFinished: () -> Unit,
  content: @Composable () -> Unit
) {
  val animationTranslationDeltaPx = with(LocalDensity.current) {
    remember(key1 = animationTranslationDelta) {
      animationTranslationDelta.toPx()
    }
  }

  var translationAnimated by remember { mutableStateOf(0f) }
  var alphaAnimated by remember { mutableStateOf(0f) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      try {
        animate(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec = tween(durationMillis = insertAnimationTotalDurationMs),
          block = { progress, _ ->
            translationAnimated = lerpFloat(animationTranslationDeltaPx, 0f, progress)
            alphaAnimated = lerpFloat(.5f, 1f, progress)
          })
      } finally {
        onAnimationFinished()
      }
    })

  Box(
    modifier = Modifier.graphicsLayer {
      translationY = translationAnimated
      alpha = alphaAnimated
    }
  ) {
    content()
  }
}

@Composable
private fun PostCell(
  isCatalogMode: Boolean,
  chanDescriptor: ChanDescriptor,
  detectLinkableClicks: Boolean,
  postCellCommentTextSizeSp: TextUnit,
  postCellSubjectTextSizeSp: TextUnit,
  postCellData: PostCellData,
  onPostBind: (PostCellData) -> Unit,
  onPostUnbind: (PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData) -> Unit,
  reparsePostSubject: suspend (PostCellData) -> AnnotatedString?
) {
  val postComment = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostComment }
  val postSubject = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostSubject }
  val postFooterText = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.postFooterText }

  DisposableEffect(
    key1 = postCellData,
    effect = {
      onPostBind(postCellData)
      onDispose { onPostUnbind(postCellData) }
    })

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(vertical = 8.dp)
  ) {
    PostCellTitle(
      chanDescriptor = chanDescriptor,
      postCellData = postCellData,
      postSubject = postSubject,
      postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
      onPostImageClicked = onPostImageClicked,
      reparsePostSubject = reparsePostSubject
    )

    PostCellComment(
      postCellData = postCellData,
      postComment = postComment,
      isCatalogMode = isCatalogMode,
      detectLinkableClicks = detectLinkableClicks,
      postCellCommentTextSizeSp = postCellCommentTextSizeSp,
      onPostCellCommentClicked = onPostCellCommentClicked
    )

    PostCellFooter(
      postCellData = postCellData,
      postFooterText = postFooterText,
      postCellCommentTextSizeSp = postCellCommentTextSizeSp,
      onPostRepliesClicked = onPostRepliesClicked
    )
  }
}

@Composable
private fun PostCellTitle(
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postSubject: AnnotatedString?,
  postCellSubjectTextSizeSp: TextUnit,
  onPostImageClicked: (ChanDescriptor, PostCellImageData) -> Unit,
  reparsePostSubject: suspend (PostCellData) -> AnnotatedString?
) {
  val chanTheme = LocalChanTheme.current
  val context = LocalContext.current

  Row(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (postCellData.images.isNotNullNorEmpty()) {
      val image = postCellData.images.first()

      Box(
        modifier = Modifier
          .wrapContentSize()
          .background(chanTheme.backColorSecondaryCompose)
          .kurobaClickable(onClick = { onPostImageClicked(chanDescriptor, image) })
      ) {
        SubcomposeAsyncImage(
          modifier = Modifier.size(60.dp),
          model = ImageRequest.Builder(context)
            .data(image.thumbnailUrl)
            .crossfade(true)
            .build(),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          alpha = 0.15f,
          content = {
            val state = painter.state
            if (state is AsyncImagePainter.State.Error) {
              logcatError {
                "PostCellTitle() url=${image.thumbnailUrl}, " +
                  "postDescriptor=${postCellData.postDescriptor}, " +
                  "error=${state.result.throwable}"
              }
            }

            SubcomposeAsyncImageContent()
          }
        )
      }

      Spacer(modifier = Modifier.width(4.dp))
    }

    if (postSubject == null) {
      // TODO(KurobaEx): shimmer animation
    } else {
      var actualPostSubject by remember { mutableStateOf(postSubject) }

      LaunchedEffect(
        key1 = postCellData,
        block = {
          val initialTime = postCellData.timeMs
            ?: return@LaunchedEffect

          while (isActive) {
            val now = System.currentTimeMillis()
            val delay = if (now - initialTime <= 60_000L) 5000L else 60_000L

            delay(delay)

            val newPostSubject = reparsePostSubject(postCellData)
              ?: continue

            actualPostSubject = newPostSubject
          }
        })

      Text(
        text = actualPostSubject,
        fontSize = postCellSubjectTextSizeSp
      )
    }
  }
}

@Composable
private fun PostCellComment(
  postCellData: PostCellData,
  postComment: AnnotatedString?,
  isCatalogMode: Boolean,
  detectLinkableClicks: Boolean,
  postCellCommentTextSizeSp: TextUnit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val clickedTextBackgroundColorMap = remember(key1 = chanTheme) { createClickableTextColorMap(chanTheme) }

  if (postComment.isNotNullNorBlank()) {
    PostCellCommentSelectionWrapper(isCatalogMode = isCatalogMode) {
      KurobaComposeClickableText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fontSize = postCellCommentTextSizeSp,
        text = postComment,
        isTextClickable = detectLinkableClicks,
        annotationBgColors = clickedTextBackgroundColorMap,
        detectClickedAnnotations = { offset, textLayoutResult, text ->
          return@KurobaComposeClickableText detectClickedAnnotations(offset, textLayoutResult, text)
        },
        onTextAnnotationClicked = { text, offset -> onPostCellCommentClicked(postCellData, text, offset) }
      )
    }
  } else if (postComment == null) {
    // TODO(KurobaEx): shimmer animation
  }
}

@Composable
private fun PostCellCommentSelectionWrapper(isCatalogMode: Boolean, content: @Composable () -> Unit) {
  val contentMovable = remember { movableContentOf(content) }

  if (isCatalogMode) {
    contentMovable()
  } else {
    SelectionContainer {
      contentMovable()
    }
  }
}

@Composable
private fun PostCellFooter(
  postCellData: PostCellData,
  postFooterText: AnnotatedString?,
  postCellCommentTextSizeSp: TextUnit,
  onPostRepliesClicked: (PostCellData) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  if (postFooterText.isNotNullNorEmpty()) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(onClick = { onPostRepliesClicked(postCellData) })
        .padding(vertical = 4.dp),
      color = chanTheme.textColorSecondaryCompose,
      fontSize = postCellCommentTextSizeSp,
      text = postFooterText
    )
  }
}

private fun createClickableTextColorMap(chanTheme: ChanTheme): Map<String, Color> {
  val postLinkColor = run {
    val resultColor = if (ThemeEngine.isDarkColor(chanTheme.postLinkColorCompose)) {
      ThemeEngine.manipulateColor(chanTheme.postLinkColorCompose, 1.2f)
    } else {
      ThemeEngine.manipulateColor(chanTheme.postLinkColorCompose, 0.8f)
    }

    return@run resultColor.copy(alpha = .4f)
  }

  return mapOf(
    PostCommentApplier.ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG to postLinkColor,
    PostCommentApplier.ANNOTATION_POST_LINKABLE to postLinkColor,
  )
}

private fun detectClickedAnnotations(
  pos: Offset,
  layoutResult: TextLayoutResult?,
  text: AnnotatedString
): AnnotatedString.Range<String>? {
  val result = layoutResult
    ?: return null
  val offset = findFirstNonNewLineCharReversed(result.getOffsetForPosition(pos), text)
    ?: return null
  val clickedAnnotations = text.getStringAnnotations(offset, offset)

  for (clickedAnnotation in clickedAnnotations) {
    if (clickedAnnotation.tag in PostCommentApplier.ALL_TAGS) {
      return clickedAnnotation
    }
  }

  return null
}

// AnnotatedString.getStringAnnotations() fails (returns no annotations) if the character
// specified by offset is a new line symbol. So we need to find the first non-newline character
// going backwards.
private fun findFirstNonNewLineCharReversed(
  inputOffset: Int,
  text: AnnotatedString
): Int? {
  var offset = inputOffset

  while (true) {
    val ch = text.getOrNull(offset)
      ?: return null

    if (ch != '\n') {
      break
    }

    --offset
  }

  return offset
}

private suspend fun PointerInputScope.processDragEvents(onPostListDragStateChanged: (Boolean) -> Unit) {
  forEachGesture {
    awaitPointerEventScope {
      val down = awaitPointerEvent(pass = PointerEventPass.Initial)
      if (down.type != PointerEventType.Press) {
        return@awaitPointerEventScope
      }

      onPostListDragStateChanged(true)

      try {
        while (true) {
          val up = awaitPointerEvent(pass = PointerEventPass.Initial)
          if (up.changes.fastAll { it.changedToUp() }) {
            break
          }

          if (up.type == PointerEventType.Release || up.type == PointerEventType.Exit) {
            break
          }
        }
      } finally {
        onPostListDragStateChanged(false)
      }
    }
  }
}

@Immutable
data class PostListOptions(
  val isCatalogMode: Boolean,
  val isInPopup: Boolean,
  val pullToRefreshEnabled: Boolean,
  val detectLinkableClicks: Boolean,
  val mainUiLayoutMode: MainUiLayoutMode,
  val contentPadding: PaddingValues,
  val postCellCommentTextSizeSp: TextUnit,
  val postCellSubjectTextSizeSp: TextUnit,
)

class PreviousPostDataInfo(
  var hash: Murmur3Hash
)