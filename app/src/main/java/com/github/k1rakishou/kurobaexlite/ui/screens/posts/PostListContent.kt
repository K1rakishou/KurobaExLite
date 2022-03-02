package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.AsyncImageScope
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.SpoilerPosition
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.logcat

private val animationTranslationDelta = 100.dp
private val animationDurationMs = 200
private val searchInfoCellHeight = 32.dp
private val postCellKeyPrefix = "post_cell"
private val threadStatusCellKey = "thread_status_cell"

@Composable
internal fun PostListContent(
  modifier: Modifier = Modifier,
  postListOptions: PostListOptions,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellClicked: (PostData) -> Unit,
  onLinkableClicked: (PostData, PostCommentParser.TextPartSpan.Linkable) -> Unit,
  onPostRepliesClicked: (PostDescriptor) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListTouchingTopOrBottomStateChanged: (Boolean) -> Unit,
  onPostListDragStateChanged: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit
) {
  val orientation = LocalConfiguration.current.orientation
  val chanDescriptor = postsScreenViewModel.chanDescriptor
  val rememberedPosition = remember(key1 = chanDescriptor, key2 = orientation) {
    postsScreenViewModel.rememberedPosition(postsScreenViewModel.chanDescriptor)
  }
  val lazyListState = rememberLazyListState(
    initialFirstVisibleItemIndex = rememberedPosition.firstVisibleItemIndex,
    initialFirstVisibleItemScrollOffset = rememberedPosition.firstVisibleItemScrollOffset
  )
  val postListAsync by postsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()

  if (chanDescriptor != null && postListAsync is AsyncData.Data) {
    LaunchedEffect(
      key1 = lazyListState.firstVisibleItemIndex,
      key2 = chanDescriptor,
      block = {
        // For debouncing purposes
        delay(50L)

        val firstVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
          ?: return@LaunchedEffect
        val lastVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
          ?: return@LaunchedEffect
        val totalCount = lazyListState.layoutInfo.totalItemsCount

        val touchingBottom = lastVisibleItemIndex >= (totalCount - 1)
        val touchingTop = firstVisibleItemIndex <= 0

        onPostListTouchingTopOrBottomStateChanged(touchingTop || touchingBottom)
      })

    LaunchedEffect(
      key1 = lazyListState.firstVisibleItemIndex,
      key2 = lazyListState.firstVisibleItemScrollOffset,
      key3 = chanDescriptor,
      block = {
        if (lazyListState.firstVisibleItemIndex <= 0) {
          return@LaunchedEffect
        }

        // For debouncing purposes
        delay(125L)

        postsScreenViewModel.rememberPosition(
          chanDescriptor = chanDescriptor,
          firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
          firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset
        )
      })

    LaunchedEffect(
      key1 = chanDescriptor,
      block = {
        postsScreenViewModel.scrollRestorationEventFlow.collect { lastRememberedPosition ->
          lazyListState.scrollToItem(
            index = lastRememberedPosition.firstVisibleItemIndex,
            scrollOffset = lastRememberedPosition.firstVisibleItemScrollOffset
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
    lazyListState = lazyListState,
    postListOptions = postListOptions,
    postListAsync = postListAsync,
    postsScreenViewModel = postsScreenViewModel,
    onPostCellClicked = onPostCellClicked,
    onPostCellCommentClicked = { postData, postComment, offset ->
      processClickedAnnotation(
        postsScreenViewModel = postsScreenViewModel,
        postData = postData,
        postComment = postComment,
        offset = offset,
        onLinkableClicked = onLinkableClicked
      )
    },
    onPostRepliesClicked = { postData ->
      onPostRepliesClicked(postData.postDescriptor)
    },
    onThreadStatusCellClicked = {
      postsScreenViewModel.resetTimer()
      postsScreenViewModel.refresh()
    },
    onPostListScrolled = onPostListScrolled,
    onPostListDragStateChanged = onPostListDragStateChanged,
    onFastScrollerDragStateChanged = onFastScrollerDragStateChanged
  )

  // This piece of code waits until postListAsync is loaded (basically when it becomes AsyncData.Data)
  // and then also wait until at least PostCell is built and drawn in the LazyColumn then sets a
  // special flag in PostScreenViewModel which then triggers previous scroll position restoration.
  // We need to do all that because otherwise we won't scroll to the last position since the list
  // state might not have the necessary info for that.
  if (
    postListAsync is AsyncData.Data
    && (postListAsync as? AsyncData.Data)?.data?.chanDescriptor != null
  ) {
    var postBuiltNotified by remember { mutableStateOf(false) }
    if (postBuiltNotified) {
      return
    }

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
          postBuiltNotified = true
          postsScreenViewModel.onPostListBuilt()
        }
      )
    }
  }
}

private fun processClickedAnnotation(
  postsScreenViewModel: PostScreenViewModel,
  postData: PostData,
  postComment: AnnotatedString,
  offset: Int,
  onLinkableClicked: (PostData, PostCommentParser.TextPartSpan.Linkable) -> Unit,
) {
  val parsedPostDataContext = postData.parsedPostDataContext
    ?: return

  val clickedAnnotations = postComment.getStringAnnotations(offset, offset)

  for (clickedAnnotation in clickedAnnotations) {
    when (clickedAnnotation.tag) {
      PostCommentApplier.ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG -> {
        if (!parsedPostDataContext.revealFullPostComment) {
          postsScreenViewModel.reparsePost(
            postData = postData,
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
          onLinkableClicked(postData, linkable)
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
          postData = postData,
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
  lazyListState: LazyListState,
  postListOptions: PostListOptions,
  postListAsync: AsyncData<AbstractPostsState>,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellClicked: (PostData) -> Unit,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListDragStateChanged: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit
) {
  val mainUiLayoutMode = postListOptions.mainUiLayoutMode
  val isInPopup = postListOptions.isInPopup
  val isCatalogMode = postListOptions.isCatalogMode

  val lastViewedPostDescriptor by postsScreenViewModel.postScreenState.lastViewedPostDescriptor.collectAsState()
  val searchQuery by postsScreenViewModel.postScreenState.searchQueryFlow.collectAsState()

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

  val previouslyVisiblePosts = remember(key1 = postsScreenViewModel.chanDescriptor, key2 = isInPopup) {
    if (isInPopup) {
      null
    } else {
      mutableStateMapOf<PostDescriptor, Unit>()
    }
  }

  val cellsPadding = remember(key1 = mainUiLayoutMode) {
    when (mainUiLayoutMode) {
      MainUiLayoutMode.Portrait -> {
        PaddingValues(horizontal = 8.dp)
      }
      MainUiLayoutMode.Split -> {
        if (isCatalogMode) {
          PaddingValues(start = 8.dp, end = 4.dp)
        } else {
          PaddingValues(start = 4.dp, end = 8.dp)
        }
      }
    }
  }

  val nestedScrollConnection = remember {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        onPostListScrolled(available.y)
        return Offset.Zero
      }
    }
  }

  Box {
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
          postListOptions = postListOptions,
          emptyContent = {
            val text = if (isCatalogMode) {
              stringResource(R.string.post_list_no_catalog_selected)
            } else {
              stringResource(R.string.post_list_no_thread_selected)
            }

            Box(
              modifier = Modifier.fillParentMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              KurobaComposeText(
                text = text
              )
            }
          },
          loadingContent = {
            KurobaComposeLoadingIndicator(
              modifier = Modifier
                .fillParentMaxSize()
                .padding(8.dp)
            )
          },
          errorContent = { postListAsyncError ->
            val errorMessage = remember(key1 = postListAsync) {
              postListAsyncError.error.errorMessageOrClassName()
            }

            KurobaComposeErrorWithButton(
              modifier = Modifier
                .fillParentMaxSize()
                .padding(8.dp),
              errorMessage = errorMessage,
              buttonText = stringResource(R.string.reload),
              onButtonClicked = { postsScreenViewModel.reload() }
            )
          },
          dataContent = { postListAsyncData ->
            postList(
              isCatalogMode = isCatalogMode,
              isInPopup = isInPopup,
              cellsPadding = cellsPadding,
              lazyListState = lazyListState,
              postsScreenViewModel = postsScreenViewModel,
              postDataList = postListAsyncData.data.posts,
              lastViewedPostDescriptor = lastViewedPostDescriptor,
              previouslyVisiblePosts = previouslyVisiblePosts,
              onPostCellClicked = onPostCellClicked,
              onPostCellCommentClicked = onPostCellCommentClicked,
              onPostRepliesClicked = onPostRepliesClicked,
              onThreadStatusCellClicked = onThreadStatusCellClicked
            )
          },
        )
      }
    )

    if (searchQuery != null) {
      SearchInfoCell(
        cellsPadding = cellsPadding,
        contentPadding = postListOptions.contentPadding,
        postsScreenViewModel = postsScreenViewModel,
        searchQuery = searchQuery
      )
    }
  }
}

private fun LazyListScope.postListAsyncDataContent(
  postListAsync: AsyncData<AbstractPostsState>,
  postListOptions: PostListOptions,
  emptyContent: @Composable LazyItemScope.() -> Unit,
  loadingContent: @Composable LazyItemScope.() -> Unit,
  errorContent: @Composable LazyItemScope.(AsyncData.Error) -> Unit,
  dataContent: (AsyncData.Data<AbstractPostsState>) -> Unit
) {
  when (postListAsync) {
    AsyncData.Empty -> {
      if (postListOptions.isInPopup) {
        item(key = "popup_loading_indicator") {
          loadingContent()
        }
      } else {
        item(key = "empty_indicator") {
          emptyContent()
        }
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
  cellsPadding: PaddingValues,
  lazyListState: LazyListState,
  postsScreenViewModel: PostScreenViewModel,
  postDataList: List<State<PostData>>,
  lastViewedPostDescriptor: PostDescriptor?,
  previouslyVisiblePosts: MutableMap<PostDescriptor, Unit>?,
  onPostCellClicked: (PostData) -> Unit,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit
) {
  val totalCount = postDataList.size
  val searchQuery = postsScreenViewModel.postScreenState.searchQueryFlow.value

  items(
    count = totalCount,
    key = { index -> "${postCellKeyPrefix}_${postDataList[index].value.postDescriptor}" },
    itemContent = { index ->
      val postData by postDataList[index]

      if (previouslyVisiblePosts != null) {
        // Pre-insert first batch of posts into the previouslyVisiblePosts so that we don't play
        // animations for recently opened catalogs/threads. We are doing this right inside of the
        // composition because otherwise there is some kind of a delay before LaunchedEffect is executed
        // so the first posts are always animated.
        if (previouslyVisiblePosts.isEmpty() && postDataList.isNotEmpty()) {
          val resultMap = mutableMapOf<PostDescriptor, Unit>()

          postDataList.forEach { postDataState ->
            resultMap[postDataState.value.postDescriptor] = Unit
          }

          previouslyVisiblePosts.putAll(resultMap)
        }
      }

      val animateInsertion = previouslyVisiblePosts != null
        && !previouslyVisiblePosts.containsKey(postData.postDescriptor)

      PostCellContainer(
        padding = cellsPadding,
        isCatalogMode = isCatalogMode,
        isInPopup = isInPopup,
        onPostCellClicked = onPostCellClicked,
        postData = postData,
        postsScreenViewModel = postsScreenViewModel,
        index = index,
        totalCount = totalCount,
        animateInsertion = animateInsertion,
        lastViewedPostDescriptor = lastViewedPostDescriptor,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostRepliesClicked = onPostRepliesClicked
      )

      if (previouslyVisiblePosts != null) {
        // Add each post into the previouslyVisiblePosts so that we don't run animations more than
        // once for each post.
        SideEffect {
          previouslyVisiblePosts[postData.postDescriptor] = Unit
        }
      }
    }
  )

  if (!isCatalogMode && searchQuery == null && postsScreenViewModel is ThreadScreenViewModel) {
    item(key = threadStatusCellKey) {
      ThreadStatusCell(
        padding = cellsPadding,
        lazyListState = lazyListState,
        threadScreenViewModel = postsScreenViewModel,
        onThreadStatusCellClicked = onThreadStatusCellClicked
      )
    }
  }
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

        if (timeUntilNextUpdateSeconds > 0L) {
          append("Loading in ${timeUntilNextUpdateSeconds}")
        } else {
          append("Loading...")
        }
      }
    }

    val combinedPaddings = remember {
      PaddingValues(
        start = padding.calculateStartPadding(LayoutDirection.Ltr),
        end = padding.calculateEndPadding(LayoutDirection.Ltr),
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
  padding: PaddingValues,
  isCatalogMode: Boolean,
  isInPopup: Boolean,
  onPostCellClicked: (PostData) -> Unit,
  postData: PostData,
  postsScreenViewModel: PostScreenViewModel,
  index: Int,
  totalCount: Int,
  animateInsertion: Boolean,
  lastViewedPostDescriptor: PostDescriptor?,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val currentlyOpenedThread by postsScreenViewModel.currentlyOpenedThreadFlow.collectAsState()

  val backgroundModifier = if (
    isCatalogMode
    && currentlyOpenedThread == postData.postDescriptor.threadDescriptor
  ) {
    Modifier.background(chanTheme.postHighlightedColorCompose)
  } else {
    Modifier
  }

  PostCellContainerAnimated(animateInsertion) {
    Column(
      modifier = Modifier
        .kurobaClickable(onClick = { onPostCellClicked(postData) })
        .then(backgroundModifier)
        .padding(padding)
    ) {
      PostCell(
        postsScreenViewModel = postsScreenViewModel,
        postData = postData,
        isCatalogMode = isCatalogMode,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostRepliesClicked = onPostRepliesClicked
      )

      val canDisplayLastViewedPostMarker = !isInPopup
        && !isCatalogMode
        && lastViewedPostDescriptor == postData.postDescriptor
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
private fun PostCellContainerAnimated(animateInsertion: Boolean, content: @Composable () -> Unit) {
  var animationInProgress by remember { mutableStateOf(false) }

  if (animateInsertion || animationInProgress) {
    animationInProgress = true

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
        animate(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec = tween(durationMillis = animationDurationMs),
          block = { progress, _ ->
            translationAnimated = lerpFloat(animationTranslationDeltaPx, 0f, progress)
            alphaAnimated = lerpFloat(.5f, 1f, progress)

            if (progress >= 1f) {
              animationInProgress = false
            }
          })
      })

      Box(
        modifier = Modifier.graphicsLayer {
          translationY = translationAnimated
          alpha = alphaAnimated
        }
      ) {
        content()
      }
  } else {
    content()
  }
}

@Composable
private fun PostCell(
  postsScreenViewModel: PostScreenViewModel,
  postData: PostData,
  isCatalogMode: Boolean,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit
) {
  var postComment by postComment(postData)
  var postSubject by postSubject(postData)
  var postFooterText by postFooterText(postData)

  DisposableEffect(
    key1 = postData,
    effect = {
      postsScreenViewModel.onPostBind(postData)

      onDispose { postsScreenViewModel.onPostUnbind(postData) }
    })

  if (postData.postCommentParsedAndProcessed == null) {
    LaunchedEffect(
      key1 = postData.postCommentUnparsed,
      key2 = postsScreenViewModel.chanDescriptor,
      block = {
        val chanDescriptor = postsScreenViewModel.chanDescriptor
          ?: return@LaunchedEffect

        val parsedPostData = postsScreenViewModel.parseComment(chanDescriptor, postData)
        postComment = parsedPostData.processedPostComment
        postSubject = parsedPostData.processedPostSubject
        postFooterText = parsedPostData.postFooterText
      }
    )
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(vertical = 8.dp)
  ) {
    PostCellTitle(
      postData = postData,
      postSubject = postSubject
    )

    PostCellComment(
      postData = postData,
      postComment = postComment,
      isCatalogMode = isCatalogMode,
      onPostCellCommentClicked = onPostCellCommentClicked
    )

    PostCellFooter(
      postData = postData,
      postFooterText = postFooterText,
      onPostRepliesClicked = onPostRepliesClicked
    )
  }

}

@Composable
private fun PostCellTitle(
  postData: PostData,
  postSubject: AnnotatedString
) {
  val chanTheme = LocalChanTheme.current

  Row(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth()
  ) {
    if (postData.images.isNotNullNorEmpty()) {
      val image = postData.images.first()

      Box(
        modifier = Modifier
          .wrapContentSize()
          .background(chanTheme.backColorSecondaryCompose)
      ) {
        AsyncImage(
          modifier = Modifier.size(60.dp),
          model = ImageRequest.Builder(LocalContext.current)
            .data(image.thumbnailUrl)
            .crossfade(true)
            .build(),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          alpha = 0.15f,
          content = { state ->
            if (state is AsyncImagePainter.State.Error) {
              logcatError {
                "PostCellTitle() url=${image.thumbnailUrl}, " +
                  "postDescriptor=${postData.postDescriptor}, " +
                  "error=${state.result.throwable}"
              }
            }

            AsyncImageScope.DefaultContent(this, state)
          }
        )
      }

      Spacer(modifier = Modifier.width(4.dp))
    }

    Text(
      text = postSubject,
      fontSize = 14.sp
    )
  }
}

@Composable
private fun PostCellComment(
  postData: PostData,
  postComment: AnnotatedString,
  isCatalogMode: Boolean,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val clickedTextBackgroundColorMap = remember(key1 = chanTheme) { createClickableTextColorMap(chanTheme) }

  if (postComment.isNotNullNorBlank()) {
    PostCellCommentSelectionWrapper(isCatalogMode = isCatalogMode) {
      KurobaComposeClickableText(
        fontSize = 14.sp,
        text = postComment,
        annotationBgColors = clickedTextBackgroundColorMap,
        detectClickedAnnotations = { offset, textLayoutResult, text ->
          return@KurobaComposeClickableText detectClickedAnnotations(offset, textLayoutResult, text)
        },
        onTextAnnotationClicked = { text, offset -> onPostCellCommentClicked(postData, text, offset) }
      )
    }
  }
}

@Composable
private fun PostCellCommentSelectionWrapper(isCatalogMode: Boolean, content: @Composable () -> Unit) {
  if (isCatalogMode) {
    content()
  } else {
    SelectionContainer {
      content()
    }
  }
}

@Composable
private fun PostCellFooter(
  postData: PostData,
  postFooterText: AnnotatedString?,
  onPostRepliesClicked: (PostData) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  if (postFooterText.isNotNullNorEmpty()) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(onClick = { onPostRepliesClicked(postData) })
        .padding(vertical = 4.dp),
      color = chanTheme.textColorSecondaryCompose,
      text = postFooterText
    )
  }
}

@Composable
private fun postSubject(postData: PostData): MutableState<AnnotatedString> {
  val postSubject = remember(
    key1 = postData.postSubjectParsedAndProcessed,
    key2 = postData.postSubjectUnparsed
  ) {
    val initial = if (postData.postSubjectParsedAndProcessed != null) {
      postData.postSubjectParsedAndProcessed!!
    } else {
      AnnotatedString(postData.postSubjectUnparsed)
    }

    mutableStateOf<AnnotatedString>(initial)
  }

  return postSubject
}

@Composable
private fun postFooterText(postData: PostData): MutableState<AnnotatedString?> {
  val postSubject = remember(key1 = postData.postFooterText) {
    mutableStateOf<AnnotatedString?>(postData.postFooterText)
  }

  return postSubject
}

@Composable
private fun postComment(postData: PostData): MutableState<AnnotatedString> {
  val postComment = remember(
    key1 = postData.postCommentParsedAndProcessed,
    key2 = postData.postCommentUnparsed
  ) {
    val initial = if (postData.postCommentParsedAndProcessed != null) {
      postData.postCommentParsedAndProcessed!!
    } else {
      AnnotatedString(postData.postCommentUnparsed)
    }

    mutableStateOf<AnnotatedString>(initial)
  }

  return postComment
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

  val offset = result.getOffsetForPosition(pos)
  val clickedAnnotations = text.getStringAnnotations(offset, offset)

  for (clickedAnnotation in clickedAnnotations) {
    if (clickedAnnotation.tag in PostCommentApplier.ALL_TAGS) {
      return clickedAnnotation
    }
  }

  return null
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

data class PostListOptions(
  val isCatalogMode: Boolean,
  val isInPopup: Boolean,
  val mainUiLayoutMode: MainUiLayoutMode,
  val contentPadding: PaddingValues,
)