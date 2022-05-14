package com.github.k1rakishou.kurobaexlite.features.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.FAB_TRANSITION_ANIMATION_DURATION_MS
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaFloatingActionButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.passClicksThrough

@OptIn(ExperimentalPagerApi::class)
@Composable
fun BoxScope.HomeScreenFloatingActionButton(
  insets: Insets,
  pagerState: PagerState,
  pagesWrapper: HomeScreenPageConverter.PagesWrapper,
  mainUiLayoutMode: MainUiLayoutMode,
  onFabClicked: (ScreenKey) -> Unit
) {
  require(pagesWrapper.pagesCount > 0) { "pagesWrapper is empty!" }

  if (mainUiLayoutMode == MainUiLayoutMode.Split) {
    return
  }

  val currentScreen = pagesWrapper.pageByIndex(pagerState.currentPage)
    ?.childScreens
    ?.firstOrNull()
    ?.composeScreen
    ?.topChildScreen()
    ?: return

  val currentScreenKey = currentScreen.screenKey
  if (currentScreen !is HomeNavigationScreen) {
    return
  }

  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val snackbarManager = koinRemember<SnackbarManager>()

  val hideableUiVisibilityInfo = remember(key1 = currentScreenKey) {
    globalUiInfoManager.getOrCreateHideableUiVisibilityInfo(currentScreenKey)
  }

  var activeSnackbarsCount by remember { mutableStateOf(0) }
  val postListScrollPosition by hideableUiVisibilityInfo.contentListScrollState.collectAsState()
  val touchingTopOrBottomOfList by hideableUiVisibilityInfo.contentListTouchingTopOrBottomState.collectAsState()
  val isDraggingPostList by hideableUiVisibilityInfo.contentListTouchingState.collectAsState()
  val isDraggingFastScroller by hideableUiVisibilityInfo.fastScrollerDragState.collectAsState()
  val screensUsingSearch by hideableUiVisibilityInfo.childScreensUsingSearch.collectAsState()
  val hasLoadError by hideableUiVisibilityInfo.hasLoadError.collectAsState()
  val replyLayoutOpened by hideableUiVisibilityInfo.replyLayoutOpened.collectAsState()
  val screenContentLoaded by currentScreen.screenContentLoadedFlow.collectAsState()

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
        activeSnackbarsCount = activeSnackbarsCount,
        screenHasFab = currentScreen.hasFab,
        screenContentLoaded = screenContentLoaded,
        hasLoadError = hasLoadError,
        replyLayoutOpened = replyLayoutOpened,
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
        tween(durationMillis = FAB_TRANSITION_ANIMATION_DURATION_MS)
      }
    },
    targetValueByState = { state ->
      when {
        !state.screenHasFab -> 0f
        !state.screenContentLoaded -> 0f
        state.activeSnackbarsCount > 0 -> 0f
        state.screensUsingSearch.isNotEmpty() -> 0f
        state.hasLoadError -> 0f
        state.replyLayoutOpened -> 0f
        state.isDraggingFastScroller -> 0f
        state.touchingTopOrBottomOfList -> 1f
        state.isDraggingPostList -> state.postListScrollPosition
        else -> if (state.postListScrollPosition > 0.5f) 1f else 0f
      }
    }
  )

  val horizOffset = dimensionResource(id = R.dimen.post_list_fab_end_offset)
  val vertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)

  KurobaFloatingActionButton(
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .graphicsLayer { this.alpha = toolbarAlpha }
      .passClicksThrough(passClicks = toolbarAlpha < 0.99f),
    iconDrawableId = R.drawable.ic_baseline_create_24,
    horizOffset = -(horizOffset),
    vertOffset = -(insets.bottom + vertOffset),
    onClick = { onFabClicked(currentScreenKey) }
  )
}

private data class CombinedFabState(
  val postListScrollPosition: Float,
  val touchingTopOrBottomOfList: Boolean,
  val isDraggingPostList: Boolean,
  val isDraggingFastScroller: Boolean,
  val activeSnackbarsCount: Int,
  val screenHasFab: Boolean,
  val screenContentLoaded: Boolean,
  val hasLoadError: Boolean,
  val replyLayoutOpened: Boolean,
  val screensUsingSearch: Set<ScreenKey>
)