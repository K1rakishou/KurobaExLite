package com.github.k1rakishou.kurobaexlite.ui.screens.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.ui.elements.*
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class CatalogScreen(componentActivity: ComponentActivity) : ComposeScreen(componentActivity) {
  private val viewModel: CatalogScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val windowInsets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current

    val contentPadding = remember(key1 = windowInsets) {
      PaddingValues(top = windowInsets.topDp, bottom = windowInsets.bottomDp)
    }

    LaunchedEffect(key1 = Unit, block = { viewModel.loadCatalog() })

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val catalogThreadsAsync = viewModel.catalogScreenState.catalogThreadsAsync

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
        ListContent(
          coroutineScope = coroutineScope,
          catalogThreadsAsync = catalogThreadsAsync,
          onPostCellClicked = { postData ->
            // TODO(KurobaEx):
          }
        )
      }
    )
  }

  private fun LazyListScope.ListContent(
    coroutineScope: CoroutineScope,
    catalogThreadsAsync: AsyncData<CatalogScreenViewModel.CatalogThreadsState>,
    onPostCellClicked: (PostData) -> Unit
  ) {
    when (catalogThreadsAsync) {
      AsyncData.Loading -> {
        item(key = "loading_indicator") {
          KurobaComposeLoadingIndicator(modifier = Modifier.fillParentMaxSize())
        }
      }
      is AsyncData.Error -> {
        item(key = "error_indicator") {
          val errorMessage = remember(key1 = catalogThreadsAsync) {
            catalogThreadsAsync.error.errorMessageOrClassName()
          }

          KurobaComposeErrorWithButton(
            modifier = Modifier.fillParentMaxSize(),
            errorMessage = errorMessage,
            buttonText = "Reload",
            onButtonClicked = {
              coroutineScope.launch { viewModel.loadCatalog() }
            }
          )
        }
      }
      is AsyncData.Data -> {
        postList(
          catalogThreadsAsync = catalogThreadsAsync,
          onPostCellClicked = onPostCellClicked
        )
      }
    }
  }

  private fun LazyListScope.postList(
    catalogThreadsAsync: AsyncData.Data<CatalogScreenViewModel.CatalogThreadsState>,
    onPostCellClicked: (PostData) -> Unit
  ) {
    val catalogThreadsState = catalogThreadsAsync.data
    val catalogThreads = catalogThreadsState.catalogThreads
    val totalCount = catalogThreads.size

    items(
      count = totalCount,
      key = { index -> catalogThreads[index].postDescriptor },
      itemContent = { index ->
        val postData = catalogThreads[index]

        PostCell(
          modifier = Modifier
            .kurobaClickable(onClick = { onPostCellClicked(postData) }),
          postData = postData
        ) {
          PostCellContent(postData, index, totalCount)
        }
      }
    )
  }

  @Composable
  private fun PostCellContent(
    postData: PostData,
    index: Int,
    totalCount: Int
  ) {
    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      fontSize = 14.sp,
      text = postData.postCommentUnparsed ?: "",
    )

    if (index < (totalCount - 1)) {
      KurobaComposeDivider(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
      )
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("CatalogScreen")
  }
}