package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme

private val DefaultPaddingValues = PaddingValues(0.dp)

fun Modifier.simpleVerticalScrollbar(
  state: LazyListState,
  chanTheme: ChanTheme,
  contentPadding: PaddingValues = DefaultPaddingValues,
  scrollbarWidth: Float,
  scrollbarMinHeight: Float,
  scrollbarDragged: Boolean
): Modifier {
  return composed(
    inspectorInfo = debugInspectorInfo {
      name = "simpleVerticalScrollbar"
      properties["chanTheme"] = chanTheme
      properties["contentPadding"] = contentPadding
      properties["scrollbarWidth"] = scrollbarWidth
      properties["scrollbarMinHeight"] = scrollbarMinHeight
      properties["scrollbarDragged"] = scrollbarDragged
    },
    factory = {
      val topPaddingPx = with(LocalDensity.current) {
        remember(key1 = contentPadding) {
          contentPadding.calculateTopPadding().toPx()
        }
      }
      val bottomPaddingPx = with(LocalDensity.current) {
        remember(key1 = contentPadding) {
          contentPadding.calculateBottomPadding().toPx()
        }
      }

      val targetAlpha = if (state.isScrollInProgress || scrollbarDragged) 0.8f else 0f
      val duration = if (state.isScrollInProgress || scrollbarDragged) 10 else 1500

      val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
      )

      this.then(
        Modifier.drawWithContent {
          drawContent()

          val layoutInfo = state.layoutInfo
          val firstVisibleElementIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index
          val needDrawScrollbar = layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
            && (state.isScrollInProgress || alpha > 0.0f)

          // Draw scrollbar if total item count is greater than visible item count and either
          // currently scrolling or if the animation is still running and lazy column has content
          if (!needDrawScrollbar || firstVisibleElementIndex == null) {
            return@drawWithContent
          }

          val (scrollbarOffsetY, scrollbarHeightAdjusted) = calculateScrollbarHeight(
            topPaddingPx = topPaddingPx,
            bottomPaddingPx = bottomPaddingPx,
            layoutInfo = layoutInfo,
            firstVisibleElementIndex = firstVisibleElementIndex,
            scrollbarMinHeight = scrollbarMinHeight,
            realScrollbarHeightDiff = null
          )

          val offsetY = topPaddingPx + scrollbarOffsetY
          val offsetX = this.size.width - scrollbarWidth

          drawRect(
            color = chanTheme.textColorHintCompose,
            topLeft = Offset(offsetX, offsetY),
            size = Size(scrollbarWidth, scrollbarHeightAdjusted),
            alpha = alpha
          )
        }
      )
    }
  )
}

private fun ContentDrawScope.calculateScrollbarHeight(
  topPaddingPx: Float,
  bottomPaddingPx: Float,
  layoutInfo: LazyListLayoutInfo,
  firstVisibleElementIndex: Int,
  scrollbarMinHeight: Float,
  realScrollbarHeightDiff: Float?
): Pair<Float, Float> {
  val totalHeightWithoutPaddings = this.size.height - (realScrollbarHeightDiff ?: 0f) - topPaddingPx - bottomPaddingPx
  val elementHeight = totalHeightWithoutPaddings / layoutInfo.totalItemsCount
  val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
  val scrollbarHeightReal = (layoutInfo.visibleItemsInfo.size * elementHeight)
  val scrollbarHeightAdjusted = scrollbarHeightReal.coerceAtLeast(scrollbarMinHeight)

  if (scrollbarHeightAdjusted > scrollbarHeightReal && realScrollbarHeightDiff == null) {
    return calculateScrollbarHeight(
      topPaddingPx = topPaddingPx,
      bottomPaddingPx = bottomPaddingPx,
      layoutInfo = layoutInfo,
      firstVisibleElementIndex = firstVisibleElementIndex,
      scrollbarMinHeight = scrollbarMinHeight,
      realScrollbarHeightDiff = (scrollbarHeightAdjusted - scrollbarHeightReal)
    )
  }

  return Pair(scrollbarOffsetY, scrollbarHeightAdjusted)
}