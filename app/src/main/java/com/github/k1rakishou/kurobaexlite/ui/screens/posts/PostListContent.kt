package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.AsyncImageScope
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.ui.elements.*
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets

@Composable
internal fun PostListContent(
  isCatalogMode: Boolean,
  postsScreenViewModel: PostScreenViewModel,
  onPostCellClicked: (PostData) -> Unit
) {
  val windowInsets = LocalWindowInsets.current
  val chanTheme = LocalChanTheme.current

  val contentPadding = remember(key1 = windowInsets) {
    PaddingValues(top = windowInsets.topDp, bottom = windowInsets.bottomDp)
  }

  val lazyListState = rememberLazyListState()
  val postListAsync = postsScreenViewModel.postScreenState.postDataAsync()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .simpleVerticalScrollbar(
        state = lazyListState,
        chanTheme = chanTheme,
        contentPadding = contentPadding
      ),
    state = lazyListState,
    contentPadding = contentPadding,
    content = {
      when (postListAsync) {
        AsyncData.Empty -> {
          item(key = "empty_indicator") {
            val text = if (isCatalogMode) {
              "No catalog selected"
            } else {
              "No thread selected"
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
              buttonText = "Reload",
              onButtonClicked = { postsScreenViewModel.reload() }
            )
          }
        }
        is AsyncData.Data -> {
          postList(
            postsScreenViewModel = postsScreenViewModel,
            postListAsync = postListAsync,
            onPostCellClicked = onPostCellClicked
          )
        }
      }
    }
  )
}

private fun LazyListScope.postList(
  postsScreenViewModel: PostScreenViewModel,
  postListAsync: AsyncData.Data<List<PostData>>,
  onPostCellClicked: (PostData) -> Unit
) {
  val postDataList = postListAsync.data
  val totalCount = postDataList.size

  items(
    count = totalCount,
    key = { index -> postDataList[index].postDescriptor },
    itemContent = { index ->
      val postData = postDataList[index]

      Column(
        modifier = Modifier
          .padding(horizontal = 4.dp)
      ) {
        PostCell(
          postsScreenViewModel = postsScreenViewModel,
          postData = postData,
          onPostCellClicked = onPostCellClicked
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
  onPostCellClicked: (PostData) -> Unit
) {
  var postComment by remember(
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

  var postSubject by remember(
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

  if (postData.postCommentParsedAndProcessed == null) {
    LaunchedEffect(
      key1 = postData.postCommentUnparsed,
      block = {
        val parsedPostData = postsScreenViewModel.parseComment(postData)
        postComment = parsedPostData.processedPostComment
        postSubject = parsedPostData.processedPostSubject
      }
    )
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .kurobaClickable(onClick = { onPostCellClicked(postData) })
  ) {
    PostCellTitle(postData = postData, postSubject = postSubject)

    PostCellComment(postComment = postComment)

    PostCellFooter()
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
      .padding(top = 4.dp)
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
          contentScale = ContentScale.Inside,
          content = { state ->
            if (state is AsyncImagePainter.State.Error) {
              logcatError { "PostCellTitle() url=${image.thumbnailUrl}, postDescriptor=${postData.postDescriptor}, error=${state.result.throwable}" }
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
  postComment: AnnotatedString
) {
  if (postComment.isNotNullNorBlank()) {
    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(vertical = 4.dp),
      fontSize = 14.sp,
      text = postComment,
    )
  }
}

@Composable
private fun PostCellFooter() {

}
