package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.AsyncImageScope
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import kotlinx.coroutines.delay
import logcat.logcat

private val animationTranslationDelta = 100.dp
private val animationDurationMs = 200

@Composable
internal fun PostListContent(
  isCatalogMode: Boolean,
  mainUiLayoutMode: MainUiLayoutMode,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellClicked: (PostData) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListTouchingTopOrBottomStateChanged: (Boolean) -> Unit,
  onPostListDragStateChanged: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit
) {
  val windowInsets = LocalWindowInsets.current
  val orientation = LocalConfiguration.current.orientation
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  val contentPadding = remember(key1 = windowInsets) {
    PaddingValues(top = toolbarHeight + windowInsets.topDp, bottom = windowInsets.bottomDp)
  }

  val chanDescriptor = postsScreenViewModel.chanDescriptor
  val rememberedPosition = remember(key1 = chanDescriptor, key2 = orientation) {
    postsScreenViewModel.rememberedPosition(postsScreenViewModel.chanDescriptor)
  }
  val lazyListState = rememberLazyListState(
    initialFirstVisibleItemIndex = rememberedPosition.firstVisibleItemIndex,
    initialFirstVisibleItemScrollOffset = rememberedPosition.firstVisibleItemScrollOffset
  )
  val postListAsync by postsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()

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
    key2 = chanDescriptor,
    block = {
      if (chanDescriptor == null || lazyListState.firstVisibleItemIndex <= 0) {
        return@LaunchedEffect
      }

      // For debouncing purposes
      delay(50L)

      postsScreenViewModel.rememberPosition(
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

  PostListInternal(
    lazyListState = lazyListState,
    contentPadding = contentPadding,
    postListAsync = postListAsync,
    isCatalogMode = isCatalogMode,
    postsScreenViewModel = postsScreenViewModel,
    mainUiLayoutMode = mainUiLayoutMode,
    onPostCellClicked = onPostCellClicked,
    onPostCellCommentClicked = { postData, postComment, offset ->
      processClickedAnnotation(postsScreenViewModel, postData, postComment, offset)
    },
    onPostRepliesClicked = { postData ->
      logcat(tag = "onPostRepliesClicked") { "Clicked replies of post ${postData.postDescriptor}" }
    },
    onThreadStatusCellClicked = { postsScreenViewModel.refresh() },
    onPostListScrolled = onPostListScrolled,
    onPostListDragStateChanged = onPostListDragStateChanged,
    onFastScrollerDragStateChanged = onFastScrollerDragStateChanged
  )
}

private fun processClickedAnnotation(
  postsScreenViewModel: PostScreenViewModel,
  postData: PostData,
  postComment: AnnotatedString,
  offset: Int
) {
  val clickedAnnotation = postComment.getStringAnnotations(offset, offset).firstOrNull()
    ?: return
  val parsedPostDataContext = postData.parsedPostDataContext
    ?: return

  when (clickedAnnotation.tag) {
    PostCommentApplier.ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG -> {
      if (!parsedPostDataContext.revealFullPostComment) {
        postsScreenViewModel.reparsePost(
          postData = postData,
          parsedPostDataContext = parsedPostDataContext.copy(revealFullPostComment = true)
        )
      }
    }
    PostCommentApplier.ANNOTATION_POST_LINKABLE -> {
      // TODO(KurobaEx):
    }
  }
}

@Composable
private fun PostListInternal(
  lazyListState: LazyListState,
  contentPadding: PaddingValues,
  postListAsync: AsyncData<AbstractPostsState>,
  isCatalogMode: Boolean,
  postsScreenViewModel: PostScreenViewModel,
  mainUiLayoutMode: MainUiLayoutMode,
  onPostCellClicked: (PostData) -> Unit,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit,
  onPostListScrolled: (Float) -> Unit,
  onPostListDragStateChanged: (Boolean) -> Unit,
  onFastScrollerDragStateChanged: (Boolean) -> Unit
) {
  val previouslyVisiblePosts = remember(key1 = postsScreenViewModel.chanDescriptor) {
    mutableStateMapOf<PostDescriptor, Unit>()
  }

  val padding = remember(key1 = mainUiLayoutMode) {
    when (mainUiLayoutMode) {
      MainUiLayoutMode.Portrait -> {
        PaddingValues(horizontal = 8.dp)
      }
      MainUiLayoutMode.TwoWaySplit -> {
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

  LazyColumnWithFastScroller(
    modifier = Modifier
      .fillMaxSize()
      .nestedScroll(nestedScrollConnection)
      .pointerInput(
        key1 = Unit,
        block = {
          processDragEvents { dragging -> onPostListDragStateChanged(dragging) }
        }
      ),
    lazyListState = lazyListState,
    contentPadding = contentPadding,
    onFastScrollerDragStateChanged = { dragging -> onFastScrollerDragStateChanged(dragging) },
    content = {
      when (postListAsync) {
        AsyncData.Empty -> {
          item(key = "empty_indicator") {
            val text = if (isCatalogMode) {
              stringResource(R.string.post_list_no_catalog_selected)
            } else {
              stringResource(R.string.post_list_no_thread_selected)
            }

            KurobaComposeText(
              modifier = Modifier.fillParentMaxSize(),
              text = text,
              textAlign = TextAlign.Center
            )
          }
        }
        AsyncData.Loading -> {
          item(key = "loading_indicator") {
            KurobaComposeLoadingIndicator(
              modifier = Modifier
                .fillParentMaxSize()
                .padding(8.dp)
            )
          }
        }
        is AsyncData.Error -> {
          item(key = "error_indicator") {
            val errorMessage = remember(key1 = postListAsync) {
              postListAsync.error.errorMessageOrClassName()
            }

            KurobaComposeErrorWithButton(
              modifier = Modifier
                .fillParentMaxSize()
                .padding(8.dp),
              errorMessage = errorMessage,
              buttonText = stringResource(R.string.reload),
              onButtonClicked = { postsScreenViewModel.reload() }
            )
          }
        }
        is AsyncData.Data -> {
          postList(
            isCatalogMode = isCatalogMode,
            padding = padding,
            postsScreenViewModel = postsScreenViewModel,
            postDataList = postListAsync.data.posts,
            previouslyVisiblePosts = previouslyVisiblePosts,
            onPostCellClicked = onPostCellClicked,
            onPostCellCommentClicked = onPostCellCommentClicked,
            onPostRepliesClicked = onPostRepliesClicked,
            onThreadStatusCellClicked = onThreadStatusCellClicked
          )
        }
      }
    }
  )
}

private fun LazyListScope.postList(
  isCatalogMode: Boolean,
  padding: PaddingValues,
  postsScreenViewModel: PostScreenViewModel,
  postDataList: List<State<PostData>>,
  previouslyVisiblePosts: MutableMap<PostDescriptor, Unit>,
  onPostCellClicked: (PostData) -> Unit,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit
) {
  val totalCount = postDataList.size

  items(
    count = totalCount,
    key = { index -> postDataList[index].value.postDescriptor },
    itemContent = { index ->
      val postData by postDataList[index]

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

      PostCellContainer(
        padding = padding,
        isCatalogMode = isCatalogMode,
        onPostCellClicked = onPostCellClicked,
        postData = postData,
        postsScreenViewModel = postsScreenViewModel,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostRepliesClicked = onPostRepliesClicked,
        index = index,
        totalCount = totalCount,
        animateInsertion = !previouslyVisiblePosts.containsKey(postData.postDescriptor)
      )

      // Add each post into the previouslyVisiblePosts so that we don't run animations more than
      // once for each post.
      SideEffect {
        previouslyVisiblePosts[postData.postDescriptor] = Unit
      }
    }
  )

  if (!isCatalogMode) {
    item(key = "thread_status_cell") {
      ThreadStatusCell(
        padding = padding,
        postsScreenViewModel = postsScreenViewModel,
        onThreadStatusCellClicked = onThreadStatusCellClicked
      )
    }
  }
}

@Composable
private fun LazyItemScope.ThreadStatusCell(
  padding: PaddingValues,
  postsScreenViewModel: PostScreenViewModel,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val threadStatusCellDataFromState by postsScreenViewModel.postScreenState.threadCellDataState.collectAsState()
  val chanDescriptorState by postsScreenViewModel.postScreenState.chanDescriptorState.collectAsState()

  val threadStatusCellData = threadStatusCellDataFromState
  val chanDescriptor = chanDescriptorState

  if (threadStatusCellData == null || (chanDescriptor == null || chanDescriptor !is ThreadDescriptor)) {
    Spacer(modifier = Modifier.height(Dp.Hairline))
    return
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .kurobaClickable(onClick = { onThreadStatusCellClicked(chanDescriptor) })
  ) {
    val threadStatusCellText = remember(key1 = threadStatusCellData) {
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
  onPostCellClicked: (PostData) -> Unit,
  postData: PostData,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit,
  index: Int,
  totalCount: Int,
  animateInsertion: Boolean
) {
  PostCellContainerAnimated(animateInsertion) {
    Column(
      modifier = Modifier
        .kurobaClickable(onClick = { onPostCellClicked(postData) })
        .padding(padding)
    ) {
      PostCell(
        postsScreenViewModel = postsScreenViewModel,
        postData = postData,
        isCatalogMode = isCatalogMode,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostRepliesClicked = onPostRepliesClicked
      )

      if (index < (totalCount - 1)) {
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
          if (up.changes.all { it.changedToUp() }) {
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