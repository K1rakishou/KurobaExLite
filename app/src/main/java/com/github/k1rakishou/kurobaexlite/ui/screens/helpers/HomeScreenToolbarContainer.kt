package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.zIndex
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel

@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeScreenToolbarContainer(
  insets: Insets,
  chanTheme: ChanTheme,
  pagerState: PagerState,
  childScreens: List<ComposeScreenWithToolbar>,
  homeScreenViewModel: HomeScreenViewModel
) {
  require(childScreens.isNotEmpty()) { "childScreens is empty!" }

  val currentPage = pagerState.currentPage.coerceIn(0, childScreens.lastIndex)
  val targetPage = pagerState.targetPage.coerceIn(0, childScreens.lastIndex)
  val animationProgress = pagerState.currentPageOffset

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val toolbarTranslationDistancePx = with(LocalDensity.current) { toolbarHeight.toPx() / 3f }
  val toolbarTotalHeight = remember(key1 = insets.topDp) { insets.topDp + toolbarHeight }
  val transitionIsProgress = currentPage != targetPage

  val postListScrollState = homeScreenViewModel.toolbarVisibilityInfo.postListScrollState.collectAsState()
  val touchingTopOrBottomOfList by homeScreenViewModel.toolbarVisibilityInfo.postListTouchingTopOrBottomState.collectAsState()
  val isDraggingPostList by homeScreenViewModel.toolbarVisibilityInfo.postListDragState.collectAsState()
  val isDraggingFastScroller by homeScreenViewModel.toolbarVisibilityInfo.fastScrollerDragState.collectAsState()

  val toolbarAlpha by when {
    isDraggingFastScroller -> animateFloatAsState(targetValue = 0f)
    touchingTopOrBottomOfList -> animateFloatAsState(targetValue = 1f)
    isDraggingPostList -> postListScrollState
    else -> {
      val targetValue = if (postListScrollState.value > 0.5f) 1f else 0f
      animateFloatAsState(targetValue = targetValue)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(toolbarTotalHeight)
      .alpha(toolbarAlpha)
      .background(chanTheme.primaryColorCompose)
      .consumeClicks()
  ) {
    Spacer(modifier = Modifier.height(insets.topDp))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarHeight)
    ) {
      val zOrders = remember(key1 = childScreens.size) { IntArray(childScreens.size) { 0 } }

      for ((pageIndex, _) in childScreens.withIndex()) {
        when (pageIndex) {
          // (Currently animated) Always behind the target and above everything else
          currentPage -> zOrders[pageIndex] = 999
          // (Currently animated) Always at the top
          targetPage -> zOrders[pageIndex] = 1000
          else -> zOrders[pageIndex] = pageIndex
        }
      }

      // TODO(KurobaEx): Bug! The state of child toolbar is reset every time the Pager is scrolled.
      //  Most likely, this is caused by BuildChildToolbar() being called from different place
      //  even though it uses key() compose helper function inside which "should", in theory, prevent that from
      //  happening but doesn't. Maybe some different helper function should be used in this case
      //  or maybe the whole approach is incorrect.
      for ((pageIndex, currentScreen) in childScreens.withIndex()) {
        val zOrder = zOrders[pageIndex]

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
              transitionIsProgress = transitionIsProgress
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
              transitionIsProgress = transitionIsProgress
            )
          }
          else -> {
            BuildChildToolbar(
              composeScreenWithToolbar = currentScreen,
              zOrder = zOrder,
              targetToolbarAlpha = 0f,
              targetToolbarTranslation = 0f,
              transitionIsProgress = transitionIsProgress
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
  transitionIsProgress: Boolean
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
      composeScreenWithToolbar.Toolbar(this)
    }
  }
}