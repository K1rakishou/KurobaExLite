package com.github.k1rakishou.kurobaexlite.features.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.zIndex
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.FAB_TRANSITION_ANIMATION_DURATION_MS
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.util.quantize
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.passClicksThrough
import com.github.k1rakishou.kurobaexlite.ui.themes.ChanTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenToolbarContainer(
  insets: Insets,
  chanTheme: ChanTheme,
  pagerState: PagerState,
  pagesWrapper: HomeScreenPageConverter.PagesWrapper,
  mainUiLayoutMode: MainUiLayoutMode,
  maxZOrder: Int = 1000
) {
  require(pagesWrapper.pagesCount >= 0) { "pagesWrapper is empty!" }

  val currentPage by remember { derivedStateOf { pagerState.currentPage } }
  val targetPage by remember { derivedStateOf { pagerState.targetPage } }

  val animationProgress by remember {
    derivedStateOf {
      pagerState.currentPageOffsetFraction
        .quantize(AppConstants.Transition.TransitionFps)
    }
  }

  if (animationProgress.isNaN()) {
    return
  }

  val currentScreenMut by remember(key1 = currentPage) {
    derivedStateOf {
      pagesWrapper.pageByIndex(currentPage)
        ?.childScreens
        ?.firstOrNull()
        ?.composeScreen
    }
  }

  val currentScreen = currentScreenMut
  if (currentScreen == null) {
    return
  }

  val currentScreenKey = currentScreen.screenKey
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()

  val hideableUiVisibilityInfo = remember(key1 = currentScreenKey) {
    globalUiInfoManager.getOrCreateHideableUiVisibilityInfo(currentScreenKey)
  }

  val currentPageIndex = currentPage.coerceIn(0, pagesWrapper.pagesCount - 1)
  val targetPageIndex = targetPage.coerceIn(0, pagesWrapper.pagesCount - 1)

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val toolbarTranslationDistancePx = with(LocalDensity.current) { toolbarHeight.toPx() / 3f }
  val toolbarTotalHeight = remember(key1 = insets.top) { insets.top + toolbarHeight }
  val transitionIsProgress = currentPageIndex != targetPageIndex

  val postListScrollPosition by hideableUiVisibilityInfo.contentListScrollState.collectAsState()
  val touchingTopOrBottomOfList by hideableUiVisibilityInfo.contentListTouchingTopOrBottomState.collectAsState()
  val isDraggingPostList by hideableUiVisibilityInfo.contentListTouchingState.collectAsState()
  val isDraggingFastScroller by hideableUiVisibilityInfo.fastScrollerDragState.collectAsState()
  val screensUsingSearch by hideableUiVisibilityInfo.childScreensUsingSearch.collectAsState()
  val replyLayoutOpened by hideableUiVisibilityInfo.replyLayoutOpened.collectAsState()
  val isInPostSelectionMode by hideableUiVisibilityInfo.isInPostSelectionMode.collectAsState()

  val combinedToolbarState by remember(key1 = currentScreenKey, key2 = mainUiLayoutMode) {
    derivedStateOf {
      CombinedToolbarState(
        currentScreenKey = currentScreenKey,
        postListScrollPosition = postListScrollPosition,
        touchingTopOrBottomOfList = touchingTopOrBottomOfList,
        isDraggingPostList = isDraggingPostList,
        isDraggingFastScroller = isDraggingFastScroller,
        replyLayoutOpened = replyLayoutOpened,
        isInPostSelectionMode = isInPostSelectionMode,
        mainUiLayoutMode = mainUiLayoutMode,
        screensUsingSearch = screensUsingSearch
      )
    }
  }

  val transition = updateTransition(
    targetState = combinedToolbarState,
    label = "toolbar transition"
  )

  val toolbarContainerAlphaState = transition.animateFloat(
    label = "toolbar container alpha animation",
    transitionSpec = {
      if (targetState.isDraggingFastScroller || targetState.touchingTopOrBottomOfList) {
        snap()
      } else {
        tween(durationMillis = FAB_TRANSITION_ANIMATION_DURATION_MS)
      }
    },
    targetValueByState = { state ->
      when {
        state.currentScreenKey in state.screensUsingSearch -> 1f
        state.replyLayoutOpened -> 1f
        state.isInPostSelectionMode -> 1f
        state.mainUiLayoutMode == MainUiLayoutMode.Split -> 1f
        state.isDraggingFastScroller -> 0f
        state.touchingTopOrBottomOfList -> 1f
        state.isDraggingPostList -> state.postListScrollPosition
        else -> if (state.postListScrollPosition > 0.5f) 1f else 0f
      }
    }
  )

  val passClicks by remember { derivedStateOf { toolbarContainerAlphaState.value < 0.99f } }
  val consumeClicks by remember { derivedStateOf { toolbarContainerAlphaState.value > 0.99f } }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(toolbarTotalHeight)
      .graphicsLayer { alpha = toolbarContainerAlphaState.value }
      .drawBehind { drawRect(chanTheme.backColor) }
      .passClicksThrough(passClicks = passClicks)
      .consumeClicks(enabled = consumeClicks)
  ) {
    Spacer(modifier = Modifier.height(insets.top))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarHeight)
    ) {
      // We need to use zIndexes to make the correct (top one of the two) toolbar clickable.
      val zOrders = remember(key1 = pagesWrapper.pagesCount) { IntArray(pagesWrapper.pagesCount) { 0 } }

      for ((pageIndex, _) in pagesWrapper.pages.withIndex()) {
        when (pageIndex) {
          // (Currently animated) Always behind the target and above everything else
          currentPageIndex -> zOrders[pageIndex] = maxZOrder - 1
          // (Currently animated) Always at the top
          targetPageIndex -> zOrders[pageIndex] = maxZOrder
          else -> zOrders[pageIndex] = pageIndex
        }
      }

      for ((pageIndex, currentPage) in pagesWrapper.pages.withIndex()) {
        val zOrder = zOrders[pageIndex]

        val screenToolbarMovable = remember(currentPage) {
          movableContentOf { currentPage.Toolbar(this) }
        }

        val childConsumeClicks by remember { derivedStateOf { transitionIsProgress && toolbarContainerAlphaState.value > 0.99f } }

        when (pageIndex) {
          currentPageIndex -> {
            val currentToolbarAlpha = lerpFloat(1f, 0f, Math.abs(animationProgress))
            val currentToolbarTranslation = if (animationProgress >= 0f) {
              lerpFloat(0f, toolbarTranslationDistancePx, Math.abs(animationProgress))
            } else {
              lerpFloat(0f, -toolbarTranslationDistancePx, Math.abs(animationProgress))
            }

            BuildChildToolbar(
              screenKey = currentScreen.screenKey,
              zOrder = zOrder,
              consumeClicks = childConsumeClicks,
              targetToolbarAlpha = currentToolbarAlpha,
              targetToolbarTranslation = currentToolbarTranslation,
              toolbarContent = { screenToolbarMovable() }
            )
          }
          targetPageIndex -> {
            val targetToolbarAlpha = lerpFloat(0f, 1f, Math.abs(animationProgress))
            val targetToolbarTranslation = if (animationProgress >= 0f) {
              lerpFloat(-toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
            } else {
              lerpFloat(toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
            }

            BuildChildToolbar(
              screenKey = currentScreen.screenKey,
              zOrder = zOrder,
              consumeClicks = childConsumeClicks,
              targetToolbarAlpha = targetToolbarAlpha,
              targetToolbarTranslation = targetToolbarTranslation,
              toolbarContent = { screenToolbarMovable() }
            )
          }
          else -> {
            BuildChildToolbar(
              screenKey = currentScreen.screenKey,
              zOrder = zOrder,
              consumeClicks = childConsumeClicks,
              targetToolbarAlpha = 0f,
              targetToolbarTranslation = 0f,
              toolbarContent = { screenToolbarMovable() }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BuildChildToolbar(
  screenKey: ScreenKey,
  zOrder: Int,
  consumeClicks: Boolean,
  targetToolbarAlpha: Float,
  targetToolbarTranslation: Float,
  toolbarContent: @Composable () -> Unit
) {
  key(screenKey) {
    Box(
      modifier = Modifier
        .zIndex(zOrder.toFloat())
        .graphicsLayer {
          alpha = targetToolbarAlpha
          translationY = targetToolbarTranslation
        }
        .consumeClicks(enabled = consumeClicks)
    ) {
      toolbarContent()
    }
  }
}

private data class CombinedToolbarState(
  val currentScreenKey: ScreenKey,
  val postListScrollPosition: Float,
  val touchingTopOrBottomOfList: Boolean,
  val isDraggingPostList: Boolean,
  val isDraggingFastScroller: Boolean,
  val replyLayoutOpened: Boolean,
  val isInPostSelectionMode: Boolean,
  val mainUiLayoutMode: MainUiLayoutMode,
  val screensUsingSearch: Set<ScreenKey>
)