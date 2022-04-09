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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp

private val DefaultPaddingValues = PaddingValues(0.dp)

sealed class ScrollbarDimens {
  data class Vertical(val width: Int, val minHeight: Int) : ScrollbarDimens()
  data class Horizontal(val height: Int, val minWidth: Int) : ScrollbarDimens()
}

fun Modifier.scrollbar(
  state: LazyListState,
  scrollbarDimens: ScrollbarDimens,
  thumbColor: Color,
  contentPadding: PaddingValues = DefaultPaddingValues,
  scrollbarDragged: Boolean
): Modifier {
  return composed(
    inspectorInfo = debugInspectorInfo {
      name = "scrollbar"
      properties["thumbColor"] = thumbColor
      properties["contentPadding"] = contentPadding
      properties["scrollbarDimens"] = scrollbarDimens
      properties["scrollbarDragged"] = scrollbarDragged
    },
    factory = {
      val density = LocalDensity.current
      val layoutDirection = LocalLayoutDirection.current

      var topPaddingPx = 0f
      var bottomPaddingPx = 0f

      var leftPaddingPx = 0f
      var rightPaddingPx = 0f

      when (scrollbarDimens) {
        is ScrollbarDimens.Horizontal -> {
          leftPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
          }

          rightPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateRightPadding(layoutDirection).toPx() }
          }
        }
        is ScrollbarDimens.Vertical -> {
          topPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateTopPadding().toPx() }
          }

          bottomPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateBottomPadding().toPx() }
          }
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

          when (scrollbarDimens) {
            is ScrollbarDimens.Horizontal -> {
              val (scrollbarOffsetX, scrollbarWidthAdjusted) = calculateScrollbarWidth(
                leftPaddingPx = leftPaddingPx,
                rightPaddingPx = rightPaddingPx,
                layoutInfo = layoutInfo,
                firstVisibleElementIndex = firstVisibleElementIndex,
                scrollbarMinWidth = scrollbarDimens.minWidth.toFloat(),
                realScrollbarWidthDiff = null
              )

              val offsetX = leftPaddingPx + scrollbarOffsetX
              val offsetY = this.size.height - scrollbarDimens.height

              drawRect(
                color = thumbColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(scrollbarWidthAdjusted, scrollbarDimens.height.toFloat()),
                alpha = alpha
              )
            }
            is ScrollbarDimens.Vertical -> {
              val (scrollbarOffsetY, scrollbarHeightAdjusted) = calculateScrollbarHeight(
                topPaddingPx = topPaddingPx,
                bottomPaddingPx = bottomPaddingPx,
                layoutInfo = layoutInfo,
                firstVisibleElementIndex = firstVisibleElementIndex,
                scrollbarMinHeight = scrollbarDimens.minHeight.toFloat(),
                realScrollbarHeightDiff = null
              )

              val offsetY = topPaddingPx + scrollbarOffsetY
              val offsetX = this.size.width - scrollbarDimens.width

              drawRect(
                color = thumbColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(scrollbarDimens.width.toFloat(), scrollbarHeightAdjusted),
                alpha = alpha
              )
            }
          }
        }
      )
    }
  )
}

private fun ContentDrawScope.calculateScrollbarWidth(
  leftPaddingPx: Float,
  rightPaddingPx: Float,
  layoutInfo: LazyListLayoutInfo,
  firstVisibleElementIndex: Int,
  scrollbarMinWidth: Float,
  realScrollbarWidthDiff: Float?
): Pair<Float, Float> {
  val totalWidthWithoutPaddings = this.size.width - (realScrollbarWidthDiff ?: 0f) - leftPaddingPx - rightPaddingPx
  val elementWidth = totalWidthWithoutPaddings / layoutInfo.totalItemsCount
  val scrollbarOffsetX = firstVisibleElementIndex * elementWidth
  val scrollbarWidthReal = (layoutInfo.visibleItemsInfo.size * elementWidth)
  val scrollbarWidthAdjusted = scrollbarWidthReal.coerceAtLeast(scrollbarMinWidth)

  if (scrollbarWidthAdjusted > scrollbarWidthReal && realScrollbarWidthDiff == null) {
    return calculateScrollbarWidth(
      leftPaddingPx = leftPaddingPx,
      rightPaddingPx = rightPaddingPx,
      layoutInfo = layoutInfo,
      firstVisibleElementIndex = firstVisibleElementIndex,
      scrollbarMinWidth = scrollbarMinWidth,
      realScrollbarWidthDiff = (scrollbarWidthAdjusted - scrollbarWidthReal)
    )
  }

  return Pair(scrollbarOffsetX, scrollbarWidthAdjusted)
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