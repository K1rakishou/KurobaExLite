package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.ui.elements.*
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
      val chanTheme = LocalChanTheme.current
      val postData = postDataList[index]

      var postComment by remember(key1 = postData.postCommentUnparsed) {
        mutableStateOf<AnnotatedString>(AnnotatedString(postData.postCommentUnparsed))
      }

      LaunchedEffect(
        key1 = postData.postCommentUnparsed,
        block = {
          postComment = withContext(Dispatchers.Default) {
            postsScreenViewModel.parseComment(chanTheme, postData)
          }
        }
      )

      Column(
        modifier = Modifier.padding(horizontal = 4.dp)
      ) {
        PostCell(onPostCellClicked, postData, postComment)

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
  onPostCellClicked: (PostData) -> Unit,
  postData: PostData,
  postComment: AnnotatedString
) {
  KurobaComposeText(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .kurobaClickable(onClick = { onPostCellClicked(postData) })
      .padding(vertical = 4.dp),
    fontSize = 14.sp,
    text = postComment,
  )
}