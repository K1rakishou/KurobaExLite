package com.github.k1rakishou.kurobaexlite.features.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.zIndex
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.FAB_TRANSITION_ANIMATION_DURATION_MS
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks

@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeScreenToolbarContainer(
  insets: Insets,
  chanTheme: ChanTheme,
  pagerState: PagerState,
  childScreens: List<ComposeScreenWithToolbar>,
  mainUiLayoutMode: MainUiLayoutMode,
  maxZOrder: Int = 1000
) {
  require(childScreens.isNotEmpty()) { "childScreens is empty!" }

  val currentScreen = childScreens.getOrNull(pagerState.currentPage) ?: return
  val currentScreenKey = currentScreen.screenKey

  if (currentScreen !is HomeNavigationScreen) {
    return
  }

  val uiInfoManager = koinRemember<UiInfoManager>()

  val toolbarVisibilityInfo = remember(key1 = currentScreenKey) {
    uiInfoManager.getOrCreateToolbarVisibilityInfo(currentScreenKey)
  }

  val currentPage = pagerState.currentPage.coerceIn(0, childScreens.lastIndex)
  val targetPage = pagerState.targetPage.coerceIn(0, childScreens.lastIndex)
  val animationProgress = pagerState.currentPageOffset

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val toolbarTranslationDistancePx = with(LocalDensity.current) { toolbarHeight.toPx() / 3f }
  val toolbarTotalHeight = remember(key1 = insets.top) { insets.top + toolbarHeight }
  val transitionIsProgress = currentPage != targetPage

  val postListScrollPosition by toolbarVisibilityInfo.postListScrollState.collectAsState()
  val touchingTopOrBottomOfList by toolbarVisibilityInfo.postListTouchingTopOrBottomState.collectAsState()
  val isDraggingPostList by toolbarVisibilityInfo.postListDragState.collectAsState()
  val isDraggingFastScroller by toolbarVisibilityInfo.fastScrollerDragState.collectAsState()
  val screensUsingSearch by toolbarVisibilityInfo.childScreensUsingSearch.collectAsState()

  val combinedToolbarState by remember(key1 = currentScreenKey) {
    derivedStateOf {
      CombinedToolbarState(
        currentScreenKey = currentScreenKey,
        postListScrollPosition = postListScrollPosition,
        touchingTopOrBottomOfList = touchingTopOrBottomOfList,
        isDraggingPostList = isDraggingPostList,
        isDraggingFastScroller = isDraggingFastScroller,
        mainUiLayoutMode = mainUiLayoutMode,
        screensUsingSearch = screensUsingSearch
      )
    }
  }

  val transition = updateTransition(
    targetState = combinedToolbarState,
    label = "toolbar transition"
  )

  val toolbarAlpha by transition.animateFloat(
    label = "toolbar alpha animation",
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
        state.mainUiLayoutMode == MainUiLayoutMode.Split -> 1f
        state.isDraggingFastScroller -> 0f
        state.touchingTopOrBottomOfList -> 1f
        state.isDraggingPostList -> state.postListScrollPosition
        else -> if (state.postListScrollPosition > 0.5f) 1f else 0f
      }
    }
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(toolbarTotalHeight)
      .graphicsLayer { this.alpha = toolbarAlpha }
      .background(chanTheme.primaryColorCompose)
      .consumeClicks()
  ) {
    Spacer(modifier = Modifier.height(insets.top))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarHeight)
    ) {
      val zOrders = remember(key1 = childScreens.size) { IntArray(childScreens.size) { 0 } }

      for ((pageIndex, _) in childScreens.withIndex()) {
        when (pageIndex) {
          // (Currently animated) Always behind the target and above everything else
          currentPage -> zOrders[pageIndex] = maxZOrder - 1
          // (Currently animated) Always at the top
          targetPage -> zOrders[pageIndex] = maxZOrder
          else -> zOrders[pageIndex] = pageIndex
        }
      }

      for ((pageIndex, currentScreen) in childScreens.withIndex()) {
        val zOrder = zOrders[pageIndex]
        val screenToolbarMovable = remember(currentScreen.screenKey) {
          movableContentOf { currentScreen.topChildScreen().Toolbar(this) }
        }

        when (pageIndex) {
          currentPage -> {
            val currentToolbarAlpha = lerpFloat(1f, 0f, Math.abs(animationProgress))
            val currentToolbarTranslation = if (animationProgress >= 0f) {
              lerpFloat(0f, toolbarTranslationDistancePx, Math.abs(animationProgress))
            } else {
              lerpFloat(0f, -toolbarTranslationDistancePx, Math.abs(animationProgress))
            }

            BuildChildToolbar(
              composeScreenWithToolbar = currentScreen,
              zOrder = zOrder,
              targetToolbarAlpha = currentToolbarAlpha,
              targetToolbarTranslation = currentToolbarTranslation,
              transitionIsProgress = transitionIsProgress,
              toolbarContent = { screenToolbarMovable() }
            )
          }
          targetPage -> {
            val targetToolbarAlpha = lerpFloat(0f, 1f, Math.abs(animationProgress))
            val targetToolbarTranslation = if (animationProgress >= 0f) {
              lerpFloat(-toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
            } else {
              lerpFloat(toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
            }

            BuildChildToolbar(
              composeScreenWithToolbar = currentScreen,
              zOrder = zOrder,
              targetToolbarAlpha = targetToolbarAlpha,
              targetToolbarTranslation = targetToolbarTranslation,
              transitionIsProgress = transitionIsProgress,
              toolbarContent = { screenToolbarMovable() }
            )
          }
          else -> {
            BuildChildToolbar(
              composeScreenWithToolbar = currentScreen,
              zOrder = zOrder,
              targetToolbarAlpha = 0f,
              targetToolbarTranslation = 0f,
              transitionIsProgress = transitionIsProgress,
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
  composeScreenWithToolbar: ComposeScreenWithToolbar,
  zOrder: Int,
  targetToolbarAlpha: Float,
  targetToolbarTranslation: Float,
  transitionIsProgress: Boolean,
  toolbarContent: @Composable () -> Unit
) {
  key(composeScreenWithToolbar.screenKey) {
    Box(
      modifier = Modifier
        .zIndex(zOrder.toFloat())
        .graphicsLayer {
          alpha = targetToolbarAlpha
          translationY = targetToolbarTranslation
        }
        .consumeClicks(consume = transitionIsProgress)
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
  val mainUiLayoutMode: MainUiLayoutMode,
  val screensUsingSearch: Set<ScreenKey>
)