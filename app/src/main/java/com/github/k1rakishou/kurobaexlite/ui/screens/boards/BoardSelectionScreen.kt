package com.github.k1rakishou.kurobaexlite.ui.screens.boards

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.LeftIconInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.MiddlePartInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreenViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class BoardSelectionScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val catalogDescriptor: CatalogDescriptor
) : ComposeScreen(componentActivity, navigationRouter) {
  private val boardSelectionScreenViewModel: BoardSelectionScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val kurobaToolbarState = remember {
      KurobaToolbarState(
        leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_arrow_back_24),
        middlePartInfo = MiddlePartInfo(centerContent = false)
      )
    }
    var searchQuery by remember { mutableStateOf<String?>(null) }

    navigationRouter.HandleBackPresses {
      popScreen()
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose)
        .consumeClicks()
    ) {
      BuildBoardsList(
        searchQuery = searchQuery,
        onBoardClicked = { clickedCatalogDescriptor ->
          catalogScreenViewModel.loadCatalog(clickedCatalogDescriptor)
          popScreen()
        }
      )

      ScreenToolbar(
        kurobaToolbarState = kurobaToolbarState,
        onBackArrowClicked = { popScreen() },
        onSearchQueryUpdated = { updatedSearchQuery -> searchQuery = updatedSearchQuery }
      )
    }
  }

  @Composable
  private fun BuildBoardsList(
    searchQuery: String?,
    onBoardClicked: (CatalogDescriptor) -> Unit
  ) {
    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copy(newTop = windowInsets.top + toolbarHeight).asPaddingValues()
    }

    val loadBoardsForSiteEvent by boardSelectionScreenViewModel
      .getOrLoadBoardsForSite(catalogDescriptor.siteKey)
      .collectAsState(initial = AsyncData.Empty)

    val filteredBoardsAsyncData by produceState(
      initialValue = loadBoardsForSiteEvent,
      key1 = searchQuery,
      key2 = loadBoardsForSiteEvent.javaClass,
      producer = {
        if (loadBoardsForSiteEvent !is AsyncData.Data || searchQuery.isNullOrEmpty()) {
          value = loadBoardsForSiteEvent
          return@produceState
        }

        val chanBoards = (loadBoardsForSiteEvent as AsyncData.Data).data
        val filteredBoards = chanBoards.filter { chanBoardUiData -> chanBoardUiData.matchesQuery(searchQuery) }

        value = AsyncData.Data(filteredBoards)
      })

    LazyColumnWithFastScroller(
      modifier = Modifier
        .fillMaxSize(),
      contentPadding = paddingValues,
      content = {
        when (val loadBoardsForSiteAsyncData = filteredBoardsAsyncData) {
          AsyncData.Empty -> {
            // no-op
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
              val errorMessage = remember(key1 = loadBoardsForSiteAsyncData) {
                loadBoardsForSiteAsyncData.error.errorMessageOrClassName()
              }

              KurobaComposeText(
                modifier = Modifier
                  .fillParentMaxSize()
                  .padding(8.dp),
                text = errorMessage
              )
            }
          }
          is AsyncData.Data -> {
            val chanBoards = loadBoardsForSiteAsyncData.data
            if (chanBoards.isEmpty()) {
              item(key = "no_boards_indicator") {
                KurobaComposeText(
                  modifier = Modifier
                    .fillParentMaxSize()
                    .padding(8.dp),
                  text = stringResource(id = R.string.board_selection_screen_no_boards_loaded)
                )
              }
            } else {
              buildChanBoardsList(chanBoards, onBoardClicked)
            }
          }
        }
      })
  }

  private fun LazyListScope.buildChanBoardsList(
    chanBoardUiDataList: List<ChanBoardUiData>,
    onBoardClicked: (CatalogDescriptor) -> Unit
  ) {
    items(
      count = chanBoardUiDataList.size,
      key = { index -> chanBoardUiDataList[index].catalogDescriptor },
      itemContent = { index ->
        val chanBoard = chanBoardUiDataList[index]
        BuildChanBoardCell(chanBoard, onBoardClicked)
      })
  }

  @Composable
  private fun BuildChanBoardCell(
    chanBoardUiData: ChanBoardUiData,
    onBoardClicked: (CatalogDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val backgroundColorModifier = if (chanBoardUiData.catalogDescriptor == catalogDescriptor) {
      Modifier.background(chanTheme.postHighlightedColorCompose)
    } else {
      Modifier
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .then(backgroundColorModifier)
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .kurobaClickable(onClick = { onBoardClicked(chanBoardUiData.catalogDescriptor) })
    ) {
      KurobaComposeText(
        text = chanBoardUiData.title,
        color = chanTheme.textColorPrimaryCompose,
        fontSize = 14.sp,
        maxLines = 1
      )

      if (chanBoardUiData.subtitle.isNotNullNorEmpty()) {
        KurobaComposeText(
          text = chanBoardUiData.subtitle,
          color = chanTheme.textColorSecondaryCompose,
          fontSize = 12.sp,
          maxLines = 3
        )
      }
    }
  }

  @Composable
  private fun ScreenToolbar(
    kurobaToolbarState: KurobaToolbarState,
    onBackArrowClicked: () -> Unit,
    onSearchQueryUpdated: (String?) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val toolbarTotalHeight = remember(key1 = insets.top) { insets.top + toolbarHeight }
    val toolbarTitle = stringResource(id = R.string.board_selection_screen_toolbar_title)

    LaunchedEffect(
      key1 = Unit,
      block = { kurobaToolbarState.toolbarTitleState.value = toolbarTitle })

    Column(
      modifier = Modifier
        .height(toolbarTotalHeight)
        .fillMaxWidth()
        .background(chanTheme.primaryColorCompose)
    ) {
      Spacer(modifier = Modifier.height(insets.top))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(toolbarHeight)
      ) {
        KurobaToolbar(
          kurobaToolbarState = kurobaToolbarState,
          navigationRouter = navigationRouter,
          onLeftIconClicked = onBackArrowClicked,
          onSearchQueryUpdated = onSearchQueryUpdated,
          onMiddleMenuClicked = null,
          onToolbarOverflowMenuClicked = null
        )
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("BoardSelectionScreen")
  }
}