package com.github.k1rakishou.kurobaexlite.features.posts.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostCell
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleSearchToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeErrorWithButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.viewmodel.ext.android.viewModel

class GlobalSearchScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val catalogDescriptor: CatalogDescriptor,
  private val onPostClicked: (PostDescriptor) -> Unit,
  private val closeCatalogSearchToolbar: () -> Unit
) : HomeNavigationScreen(componentActivity, navigationRouter) {
  private val globalSearchScreenViewModel: GlobalSearchScreenViewModel by componentActivity.viewModel()

  private val postSearchLongtapContentMenu by lazy {
    PostSearchLongtapContentMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  override val screenContentLoadedFlow: StateFlow<Boolean> by lazy { MutableStateFlow(true) }
  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false
  override val dragToCloseEnabledState: MutableState<Boolean> = mutableStateOf(true)

  private val searchToolbarKey = "${screenKey.key}_search"

  private val searchToolbar: KurobaChildToolbar by lazy {
    SimpleSearchToolbar(
      initialSearchQuery = globalSearchScreenViewModel.searchQueryState.value,
      toolbarKey = searchToolbarKey,
      onSearchQueryUpdated = { searchQuery ->
        globalSearchScreenViewModel.updateSearchQuery(searchQuery, catalogDescriptor)
      },
      closeSearch = {
        kurobaToolbarContainerState.popToolbar(searchToolbarKey)
        popScreen()
      }
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<KurobaChildToolbar>(screenKey)
  }

  override fun onStartDisposing() {
    super.onStartDisposing()

    closeCatalogSearchToolbar()
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    KurobaToolbarContainer(
      toolbarContainerKey = screenKey.key,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = { true }
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    val windowInsets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        kurobaToolbarContainerState.setToolbar(searchToolbar)
      }
    )

    val lazyListState = rememberLazyListState()
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copy(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = windowInsets.top + toolbarHeight
      ).asPaddingValues()
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose)
        .consumeClicks()
    ) {
      GlobalSearchList(
        lazyListState = lazyListState,
        paddingValues = paddingValues
      )
    }
  }

  @Composable
  private fun GlobalSearchList(
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
  ) {
    val searchQuery by globalSearchScreenViewModel.searchQueryState

    if (searchQuery.isNullOrEmpty()) {
      SearchQueryIsEmpty()
      return
    }

    val postsAsync by globalSearchScreenViewModel.postsAsyncState
    val endReached by globalSearchScreenViewModel.endReachedState
    val currentPage by globalSearchScreenViewModel.currentPageState

    val pageLoadErrorMut by globalSearchScreenViewModel.pageLoadErrorState
    val pageLoadError = pageLoadErrorMut

    LazyColumnWithFastScroller(
      lazyListContainerModifier = Modifier.fillMaxSize(),
      lazyListModifier = Modifier.fillMaxSize(),
      lazyListState = lazyListState,
      contentPadding = paddingValues,
      content = {
        val foundPosts = when (val state = postsAsync) {
          AsyncData.Uninitialized -> {
            // no-op
            return@LazyColumnWithFastScroller
          }
          is AsyncData.Error -> {
            item(key = "list_error") { ListErrorContent(state) }
            return@LazyColumnWithFastScroller
          }
          AsyncData.Loading -> {
            item(key = "list_loading") { ListLoadingContent() }
            return@LazyColumnWithFastScroller
          }
          is AsyncData.Data -> state.data
        }

        if (foundPosts.isEmpty()) {
          item(key = "list_is_empty") { ListEmptyContent() }
          return@LazyColumnWithFastScroller
        }

        items(
          count = foundPosts.size,
          key = { index -> foundPosts[index].postDescriptor },
          itemContent = { index ->
            val postCellData = foundPosts[index]

            PostSearchCell(
              index = index,
              totalCount = foundPosts.size,
              postCellData = postCellData
            )
          }
        )

        if (pageLoadError != null) {
          item(
            key = "page_load_error",
            content = { PageLoadErrorContent(pageLoadError) }
          )
        } else if (endReached) {
          item(
            key = "end_reached",
            content = { EndReachedIndicator() }
          )
        } else {
          item(
            key = "load_more_indicator_${currentPage}",
            content = { LoadMoreIndicator(currentPage, lazyListState) }
          )
        }
      }
    )
  }

  @Composable
  private fun PostSearchCell(
    index: Int,
    totalCount: Int,
    postCellData: PostCellData
  ) {
    val postCellCommentTextSizeSp by globalUiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by globalUiInfoManager.postCellSubjectTextSizeSp.collectAsState()
    val cellsPadding = remember { PaddingValues(horizontal = 8.dp) }

    Column(
      modifier = Modifier
        .padding(cellsPadding)
        .kurobaClickable(
          onClick = {
            val currentUiLayoutMode = globalUiInfoManager.currentUiLayoutMode
              ?: return@kurobaClickable

            onPostClicked(postCellData.postDescriptor)

            if (dragToCloseEnabled && currentUiLayoutMode != MainUiLayoutMode.Split) {
              popScreen()
            }
          },
          onLongClick = {
            postSearchLongtapContentMenu.showMenu(postCellData)
          }
        )
    ) {
      PostCell(
        isCatalogMode = true,
        chanDescriptor = catalogDescriptor,
        detectLinkableClicks = false,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        postCellData = postCellData,
        postCellControlsShown = false,
        onSelectionModeChanged = {
          // no-op
        },
        onPostBind = {
          // no-op
        },
        onPostUnbind = {
          // no-op
        },
        onPostCellCommentClicked = { _, _, _ ->
          // no-op
        },
        onPostCellCommentLongClicked = {  _, _, _ ->
          // no-op
        },
        onPostRepliesClicked = {
          // no-op
        },
        onQuotePostClicked = {
          // no-op
        },
        onQuotePostWithCommentClicked = {
          // no-op
        },
        onPostImageClicked = { _, _, _ ->
          // no-op
        },
        reparsePostSubject = {
          // no-op
          null
        }
      )

      if (index < (totalCount - 1)) {
        KurobaComposeDivider(
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }

  @Composable
  private fun LazyItemScope.PageLoadErrorContent(throwable: Throwable) {
    val currentPage by globalSearchScreenViewModel.currentPageState
    val errorMessage = remember(key1 = throwable) { throwable.errorMessageOrClassName() }

    KurobaComposeErrorWithButton(
      modifier = Modifier
        .fillParentMaxSize()
        .padding(8.dp),
      errorMessage = errorMessage,
      buttonText = stringResource(R.string.reload),
      onButtonClicked = { globalSearchScreenViewModel.reloadCurrentPage(currentPage) }
    )
  }

  @Composable
  private fun LazyItemScope.ListErrorContent(
    screenStateAsyncError: AsyncData.Error,
  ) {
    val errorMessage = remember(key1 = screenStateAsyncError) {
      screenStateAsyncError.error.errorMessageOrClassName()
    }

    KurobaComposeErrorWithButton(
      modifier = Modifier
        .fillParentMaxSize()
        .padding(8.dp),
      errorMessage = errorMessage,
      buttonText = stringResource(R.string.reload),
      onButtonClicked = { globalSearchScreenViewModel.fullReload() }
    )
  }

  @Composable
  private fun LazyItemScope.EndReachedIndicator() {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(42.dp)
        .padding(8.dp),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeText(text = stringResource(id = R.string.global_search_screen_end_of_results_reached))
    }
  }

  @Composable
  private fun LazyItemScope.LoadMoreIndicator(currentPage: Int, lazyListState: LazyListState) {
    val currentLoadingPage by globalSearchScreenViewModel.currentLoadingPageState

    KurobaComposeLoadingIndicator(
      modifier = Modifier
        .fillMaxWidth()
        .height(42.dp)
        .padding(8.dp)
    )

    if (currentPage <= 0 || currentPage == currentLoadingPage) {
      return
    }

    val isLoadingIndicatorForPageVisible by remember(key1 = currentPage) {
      derivedStateOf {
        lazyListState.layoutInfo.visibleItemsInfo
          .lastOrNull { lazyListItemInfo -> lazyListItemInfo.key == "load_more_indicator_${currentPage}" } != null
      }
    }

    if (!isLoadingIndicatorForPageVisible) {
      return
    }

    LaunchedEffect(
      key1 = currentPage,
      block = { globalSearchScreenViewModel.onNextPageRequested(currentPage, currentLoadingPage) }
    )
  }

  @Composable
  private fun LazyItemScope.ListLoadingContent() {
    KurobaComposeLoadingIndicator(
      modifier = Modifier
        .fillParentMaxSize()
        .padding(8.dp)
    )
  }

  @Composable
  private fun SearchQueryIsEmpty() {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeText(text = stringResource(id = R.string.global_search_screen_search_query_is_empty))
    }
  }

  @Composable
  private fun LazyItemScope.ListEmptyContent() {
    val searchQueryMut by globalSearchScreenViewModel.searchQueryState
    val searchQuery = searchQueryMut

    if (searchQuery == null) {
      return
    }

    Box(
      modifier = Modifier.fillParentMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      val text = stringResource(id = R.string.global_search_screen_nothing_found_by_query, searchQuery)
      KurobaComposeText(text = text)
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("GlobalSearchScreen")
  }

}