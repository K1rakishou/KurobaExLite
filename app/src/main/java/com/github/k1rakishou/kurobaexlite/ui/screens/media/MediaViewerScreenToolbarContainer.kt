package com.github.k1rakishou.kurobaexlite.ui.screens.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks

@OptIn(ExperimentalPagerApi::class)
@Composable
fun MediaViewerScreenToolbarContainer(
  toolbarHeight: Dp,
  pagerState: PagerState,
  childToolbars: List<ChildToolbar>,
  maxZOrder: Int = 1000
) {
  check(childToolbars.size <= 2) { "Can only handle 2 toolbars at once" }

  val insets = LocalWindowInsets.current

  val currentToolbarIndex = pagerState.currentPage
  val targetToolbarIndex = pagerState.targetPage
  val animationProgress = pagerState.currentPageOffset.coerceIn(-1f, 1f)

  val toolbarTranslationDistancePx = with(LocalDensity.current) { toolbarHeight.toPx() / 3f }
  val toolbarTotalHeight = remember(key1 = insets.top) { insets.top + toolbarHeight }
  val transitionIsProgress = currentToolbarIndex != targetToolbarIndex

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(toolbarTotalHeight)
      .consumeClicks()
  ) {
    Spacer(modifier = Modifier.height(insets.top))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarHeight)
    ) {
      val zOrders = remember(key1 = childToolbars.size) {
        IntArray(childToolbars.size) { 0 }
      }

      for ((indexInZOrders, childToolbar) in childToolbars.withIndex()) {
        when (childToolbar.indexInList) {
          // (Currently animated) Always behind the target and above everything else
          currentToolbarIndex -> zOrders[indexInZOrders] = maxZOrder - 1
          // (Currently animated) Always at the top
          targetToolbarIndex -> zOrders[indexInZOrders] = maxZOrder
          else -> zOrders[indexInZOrders] = indexInZOrders
        }
      }

      for ((indexInZOrders, currentToolbar) in childToolbars.withIndex()) {
        val zOrder = zOrders[indexInZOrders]

        val screenToolbarMovable = remember(currentToolbar.key) {
          movableContentOf { currentToolbar.content(this) }
        }

        when (currentToolbar.indexInList) {
          currentToolbarIndex -> {
            val currentToolbarAlpha = lerpFloat(1f, 0f, Math.abs(animationProgress))
            val currentToolbarTranslation = if (animationProgress >= 0f) {
              lerpFloat(0f, toolbarTranslationDistancePx, Math.abs(animationProgress))
            } else {
              lerpFloat(0f, -toolbarTranslationDistancePx, Math.abs(animationProgress))
            }

            BuildChildToolbar(
              childToolbar = currentToolbar,
              zOrder = zOrder,
              targetToolbarAlpha = currentToolbarAlpha,
              targetToolbarTranslation = currentToolbarTranslation,
              transitionIsProgress = transitionIsProgress,
              toolbarContent = { screenToolbarMovable() }
            )
          }
          targetToolbarIndex -> {
            val targetToolbarAlpha = lerpFloat(0f, 1f, Math.abs(animationProgress))
            val targetToolbarTranslation = if (animationProgress >= 0f) {
              lerpFloat(-toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
            } else {
              lerpFloat(toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
            }

            BuildChildToolbar(
              childToolbar = currentToolbar,
              zOrder = zOrder,
              targetToolbarAlpha = targetToolbarAlpha,
              targetToolbarTranslation = targetToolbarTranslation,
              transitionIsProgress = transitionIsProgress,
              toolbarContent = { screenToolbarMovable() }
            )
          }
          else -> {
            BuildChildToolbar(
              childToolbar = currentToolbar,
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
  childToolbar: ChildToolbar,
  zOrder: Int,
  targetToolbarAlpha: Float,
  targetToolbarTranslation: Float,
  transitionIsProgress: Boolean,
  toolbarContent: @Composable () -> Unit
) {
  key(childToolbar.key) {
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

class ChildToolbar(
  val key: Any,
  val indexInList: Int,
  val content: @Composable BoxScope.() -> Unit
)