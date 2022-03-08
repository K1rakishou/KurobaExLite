package com.github.k1rakishou.kurobaexlite.ui.screens.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaFloatingActionButton
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey

@OptIn(ExperimentalPagerApi::class)
@Composable
fun BoxScope.HomeScreenFloatingActionButton(
  animationDurationMs: Int,
  insets: Insets,
  pagerState: PagerState,
  childScreens: List<ComposeScreenWithToolbar>,
  mainUiLayoutMode: MainUiLayoutMode,
  homeScreenViewModel: HomeScreenViewModel,
  snackbarManager: SnackbarManager
) {
  require(childScreens.isNotEmpty()) { "childScreens is empty!" }

  if (mainUiLayoutMode == MainUiLayoutMode.Split) {
    return
  }

  val currentScreen = childScreens.getOrNull(pagerState.currentPage) ?: return
  val currentScreenKey = currentScreen.screenKey

  if (currentScreen !is HomeNavigationScreen) {
    return
  }

  val currentPage = pagerState.currentPage.coerceIn(0, childScreens.lastIndex)
  val targetPage = pagerState.targetPage.coerceIn(0, childScreens.lastIndex)
  val transitionIsProgress = currentPage != targetPage

  var activeSnackbarsCount by remember { mutableStateOf(0) }
  val postListScrollPosition by homeScreenViewModel.toolbarVisibilityInfo.postListScrollState.collectAsState()
  val touchingTopOrBottomOfList by homeScreenViewModel.toolbarVisibilityInfo.postListTouchingTopOrBottomState.collectAsState()
  val isDraggingPostList by homeScreenViewModel.toolbarVisibilityInfo.postListDragState.collectAsState()
  val isDraggingFastScroller by homeScreenViewModel.toolbarVisibilityInfo.fastScrollerDragState.collectAsState()
  val screensUsingSearch by homeScreenViewModel.toolbarVisibilityInfo.childScreensUsingSearch.collectAsState()

  LaunchedEffect(
    key1 = currentScreenKey,
    block = {
      snackbarManager
        .listenForActiveSnackbarsFlow(currentScreenKey)
        .collect { activeSnackbars -> activeSnackbarsCount = activeSnackbars.size }
    })

  val combinedFabState by remember(key1 = currentScreen) {
    derivedStateOf {
      CombinedFabState(
        postListScrollPosition = postListScrollPosition,
        touchingTopOrBottomOfList = touchingTopOrBottomOfList,
        isDraggingPostList = isDraggingPostList,
        isDraggingFastScroller = isDraggingFastScroller,
        mainUiLayoutMode = mainUiLayoutMode,
        activeSnackbarsCount = activeSnackbarsCount,
        screenHasFab = currentScreen.hasFab,
        // TODO(KurobaEx): screenContentLoaded doesn't work
        screenContentLoaded = true, // currentScreen.screenContentLoaded,
        screensUsingSearch = screensUsingSearch
      )
    }
  }

  val transition = updateTransition(
    targetState = combinedFabState,
    label = "fab transition"
  )

  val toolbarAlpha by transition.animateFloat(
    label = "fab alpha animation",
    transitionSpec = {
      if (targetState.isDraggingFastScroller || targetState.touchingTopOrBottomOfList) {
        snap()
      } else {
        tween(durationMillis = animationDurationMs)
      }
    },
    targetValueByState = { state ->
      when {
        !state.screenHasFab || !state.screenContentLoaded -> 0f
        state.activeSnackbarsCount > 0 -> 0f
        state.screensUsingSearch.isNotEmpty() -> 1f
        state.mainUiLayoutMode == MainUiLayoutMode.Split -> 1f
        state.isDraggingFastScroller -> 0f
        state.touchingTopOrBottomOfList -> 1f
        state.isDraggingPostList -> state.postListScrollPosition
        else -> if (state.postListScrollPosition > 0.5f) 1f else 0f
      }
    }
  )

  KurobaFloatingActionButton(
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .offset(y = -(insets.bottom + 16.dp), x = -(24.dp))
      .alpha(toolbarAlpha),
    iconDrawableId = R.drawable.ic_baseline_create_24,
    onClick = {
      if (transitionIsProgress) {
        return@KurobaFloatingActionButton
      }

      homeScreenViewModel.onFabClicked(currentScreenKey)
    }
  )
}

private data class CombinedFabState(
  val postListScrollPosition: Float,
  val touchingTopOrBottomOfList: Boolean,
  val isDraggingPostList: Boolean,
  val isDraggingFastScroller: Boolean,
  val mainUiLayoutMode: MainUiLayoutMode,
  val activeSnackbarsCount: Int,
  val screenHasFab: Boolean,
  val screenContentLoaded: Boolean,
  val screensUsingSearch: Set<ScreenKey>
)