package com.github.k1rakishou.kurobaexlite.ui.elements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme

private val DefaultPaddingValues = PaddingValues(0.dp)
private val SCROLLBAR_WIDTH = 8.dp

fun Modifier.simpleVerticalScrollbar(
  state: LazyListState,
  chanTheme: ChanTheme,
  contentPadding: PaddingValues = DefaultPaddingValues,
  width: Dp = SCROLLBAR_WIDTH
): Modifier {
  return composed {
    val targetAlpha = if (state.isScrollInProgress) 0.8f else 0f
    val duration = if (state.isScrollInProgress) 10 else 1500

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

        val topPaddingPx = contentPadding.calculateTopPadding().toPx()
        val bottomPaddingPx = contentPadding.calculateBottomPadding().toPx()
        val totalHeightWithoutPaddings = this.size.height - topPaddingPx - bottomPaddingPx

        val elementHeight = totalHeightWithoutPaddings / layoutInfo.totalItemsCount
        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
        val scrollbarHeight = layoutInfo.visibleItemsInfo.size * elementHeight

        drawRect(
          color = chanTheme.textColorHintCompose,
          topLeft = Offset(this.size.width - width.toPx(), topPaddingPx + scrollbarOffsetY),
          size = Size(width.toPx(), scrollbarHeight),
          alpha = alpha
        )
      }
    )
  }
}