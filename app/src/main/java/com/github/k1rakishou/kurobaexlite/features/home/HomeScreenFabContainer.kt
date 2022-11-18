package com.github.k1rakishou.kurobaexlite.features.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.FAB_TRANSITION_ANIMATION_DURATION_MS
import com.github.k1rakishou.kurobaexlite.features.posts.thread.PostFollowStack
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.quantize
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
fun BoxScope.HomeScreenFabContainer(
  insets: Insets,
  pagerState: PagerState,
  pagesWrapper: HomeScreenPageConverter.PagesWrapper,
  mainUiLayoutMode: MainUiLayoutMode,
  onGoBackFabClicked: () -> Unit,
  onReplyFabClicked: (ScreenKey) -> Unit
) {
  require(pagesWrapper.pagesCount > 0) { "pagesWrapper is empty!" }

  if (mainUiLayoutMode == MainUiLayoutMode.Split) {
    return
  }

  val currentPage by remember { derivedStateOf { pagerState.currentPage } }

  val currentScreenMut by remember(key1 = currentPage) {
    derivedStateOf {
      pagesWrapper.pageByIndex(currentPage)
        ?.childScreens
        ?.firstOrNull()
        ?.composeScreen
        ?.topChildScreen()
    }
  }

  val currentScreen = currentScreenMut
  if (currentScreen == null) {
    return
  }

  if (currentScreen !is HomeNavigationScreen) {
    return
  }

  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val snackbarManager = koinRemember<SnackbarManager>()
  val postFollowStack = koinRemember<PostFollowStack>()
  val currentScreenKey = currentScreen.screenKey

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

  val combinedFabState by remember(key1 = currentScreen, key2 = mainUiLayoutMode) {
    derivedStateOf {
      CombinedFabState(
        mainUiLayoutMode = mainUiLayoutMode,
        postListScrollPosition = postListScrollPosition
          .quantize(AppConstants.Transition.TransitionFps),
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

  val toolbarAlphaState = transition.animateFloat(
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
        state.mainUiLayoutMode == MainUiLayoutMode.Split -> 1f
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

  val passClicks by remember { derivedStateOf { toolbarAlphaState.value < 0.99f } }
  val horizOffset = dimensionResource(id = R.dimen.post_list_fab_end_offset)
  val vertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)

  Column(
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .offset { IntOffset(x = -(horizOffset.roundToPx()), y = -(insets.bottom + vertOffset).roundToPx()) }
  ) {
    if (currentScreenKey == ThreadScreen.SCREEN_KEY) {
      val isHistoryEmpty by postFollowStack.isHistoryEmpty
      if (!isHistoryEmpty) {
        KurobaFloatingActionButton(
          modifier = Modifier
            .graphicsLayer { this.alpha = toolbarAlphaState.value }
            .passClicksThrough(passClicks = passClicks),
          iconDrawableId = R.drawable.ic_baseline_arrow_back_24,
          backgroundColor = Color.White,
          onClick = { onGoBackFabClicked() }
        )

        Spacer(modifier = Modifier.height(24.dp))
      }
    }

    KurobaFloatingActionButton(
      modifier = Modifier
        .graphicsLayer { this.alpha = toolbarAlphaState.value }
        .passClicksThrough(passClicks = passClicks),
      iconDrawableId = R.drawable.ic_baseline_create_24,
      onClick = { onReplyFabClicked(currentScreenKey) }
    )
  }
}

private data class CombinedFabState(
  val mainUiLayoutMode: MainUiLayoutMode,
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