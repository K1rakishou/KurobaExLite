package com.github.k1rakishou.kurobaexlite.ui.screens.boards

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanBoard
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
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
        onBoardClicked = { clickedCatalogDescriptor ->
          catalogScreenViewModel.loadCatalog(clickedCatalogDescriptor)
          popScreen()
        }
      )

      ScreenToolbar(onBackArrowClicked = { popScreen() })
    }
  }

  @Composable
  private fun BuildBoardsList(
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

    LazyColumnWithFastScroller(
      modifier = Modifier
        .fillMaxSize(),
      contentPadding = paddingValues,
      content = {
        when (val loadBoardsForSiteAsyncData = loadBoardsForSiteEvent) {
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
    chanBoards: List<ChanBoard>,
    onBoardClicked: (CatalogDescriptor) -> Unit
  ) {
    items(
      count = chanBoards.size,
      key = { index -> chanBoards[index].catalogDescriptor },
      itemContent = { index ->
        val chanBoard = chanBoards[index]
        BuildChanBoardCell(chanBoard, onBoardClicked)
      })
  }

  @Composable
  private fun BuildChanBoardCell(
    chanBoard: ChanBoard,
    onBoardClicked: (CatalogDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val backgroundColorModifier = if (chanBoard.catalogDescriptor == catalogDescriptor) {
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
        .kurobaClickable(onClick = { onBoardClicked(chanBoard.catalogDescriptor) })
    ) {
      KurobaComposeText(
        text = buildString {
          append("/")
          append(chanBoard.catalogDescriptor.boardCode)
          append("/")

          if (chanBoard.boardTitle.isNotNullNorEmpty()) {
            append(" — ")
            append(chanBoard.boardTitle)
          }
        },
        color = chanTheme.textColorPrimaryCompose,
        fontSize = 14.sp,
        maxLines = 1
      )

      if (chanBoard.boardDescription.isNotNullNorEmpty()) {
        KurobaComposeText(
          text = "${chanBoard.boardDescription}",
          color = chanTheme.textColorSecondaryCompose,
          fontSize = 12.sp,
          maxLines = 3
        )
      }
    }
  }

  @Composable
  private fun ScreenToolbar(onBackArrowClicked: () -> Unit) {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val toolbarTotalHeight = remember(key1 = insets.top) { insets.top + toolbarHeight }

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
        KurobaToolbarLayout(
          leftPart = {
            KurobaComposeIcon(
              modifier = Modifier
                .size(24.dp)
                .kurobaClickable(
                  bounded = false,
                  onClick = { onBackArrowClicked() }
                ),
              drawableId = R.drawable.ic_baseline_arrow_back_24
            )
          },
          middlePart = {
            KurobaComposeText(
              text = stringResource(id = R.string.board_selection_screen_toolbar_title),
              color = Color.White
            )
          },
          rightPart = {}
        )
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("BoardSelectionScreen")
  }
}