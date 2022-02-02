package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
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
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import logcat.logcat


@Composable
internal fun PostListContent(
  isCatalogMode: Boolean,
  mainUiLayoutMode: MainUiLayoutMode,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellClicked: (PostData) -> Unit
) {
  val windowInsets = LocalWindowInsets.current
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  val contentPadding = remember(key1 = windowInsets) {
    PaddingValues(top = toolbarHeight + windowInsets.topDp, bottom = windowInsets.bottomDp)
  }

  val lazyListState = rememberLazyListState()
  val postListAsync = postsScreenViewModel.postScreenState.postDataAsyncState()

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
    }
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
  postListAsync: AsyncData<List<State<PostData>>>,
  isCatalogMode: Boolean,
  postsScreenViewModel: PostScreenViewModel,
  mainUiLayoutMode: MainUiLayoutMode,
  onPostCellClicked: (PostData) -> Unit,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit
) {
  LazyColumnWithFastScroller(
    modifier = Modifier.fillMaxSize(),
    lazyListState = lazyListState,
    contentPadding = contentPadding,
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
            mainUiLayoutMode = mainUiLayoutMode,
            postsScreenViewModel = postsScreenViewModel,
            postListAsync = postListAsync,
            onPostCellClicked = onPostCellClicked,
            onPostCellCommentClicked = onPostCellCommentClicked,
            onPostRepliesClicked = onPostRepliesClicked
          )
        }
      }
    }
  )
}

private fun LazyListScope.postList(
  isCatalogMode: Boolean,
  mainUiLayoutMode: MainUiLayoutMode,
  postsScreenViewModel: PostScreenViewModel,
  postListAsync: AsyncData.Data<List<State<PostData>>>,
  onPostCellClicked: (PostData) -> Unit,
  onPostCellCommentClicked: (PostData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostData) -> Unit
) {
  val postDataList = postListAsync.data
  val totalCount = postDataList.size

  items(
    count = totalCount,
    key = { index -> postDataList[index].value.postDescriptor },
    itemContent = { index ->
      val postData by postDataList[index]

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
            modifier = Modifier
              .fillMaxWidth()
          )
        }
      }
    }
  )
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

  if (postData.postCommentParsedAndProcessed == null) {
    LaunchedEffect(
      key1 = postData.postCommentUnparsed,
      block = {
        val parsedPostData = postsScreenViewModel.parseComment(isCatalogMode, postData)
        postComment = parsedPostData.processedPostComment
        postSubject = parsedPostData.processedPostSubject
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
      postsScreenViewModel = postsScreenViewModel,
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
  val clickedTextBackgroundColorMap = remember(key1 = chanTheme) {
    return@remember createClickableTextColorMap(chanTheme)
  }

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
    return
  }

  SelectionContainer {
    content()
  }
}

@Composable
private fun PostCellFooter(
  postData: PostData,
  postsScreenViewModel: PostScreenViewModel,
  onPostRepliesClicked: (PostData) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val context = LocalContext.current
  var postFooterText by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(
    key1 = postData,
    block = {
      postFooterText = formatFooterText(postData, context, postsScreenViewModel)
    }
  )

  val repliesText = postFooterText

  if (repliesText.isNotNullNorEmpty()) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(onClick = { onPostRepliesClicked(postData) })
        .padding(vertical = 4.dp),
      color = chanTheme.textColorSecondaryCompose,
      text = repliesText
    )
  }
}

private suspend fun formatFooterText(
  postData: PostData,
  context: Context,
  postsScreenViewModel: PostScreenViewModel
): String? {
  if (postData is OriginalPostData) {
    return buildString {
      postData.threadRepliesTotal
        ?.takeIf { repliesCount -> repliesCount > 0 }
        ?.let { repliesCount ->
          val repliesText = context.resources.getQuantityString(
            R.plurals.reply_with_number,
            repliesCount,
            repliesCount
          )

          append(repliesText)
        }

      postData.threadImagesTotal
        ?.takeIf { imagesCount -> imagesCount > 0 }
        ?.let { imagesCount ->
          if (isNotEmpty()) {
            append(", ")
          }

          val imagesText = context.resources.getQuantityString(
            R.plurals.image_with_number,
            imagesCount,
            imagesCount
          )

          append(imagesText)
        }
    }
  }

  val repliesFrom = postsScreenViewModel.getRepliesFrom(postData.postDescriptor)
  if (repliesFrom.isNotEmpty()) {
    val repliesFromCount = repliesFrom.size

    return context.resources.getQuantityString(
      R.plurals.reply_with_number,
      repliesFromCount,
      repliesFromCount
    )
  }

  return null
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
