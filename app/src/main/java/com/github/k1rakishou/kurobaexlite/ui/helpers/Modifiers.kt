package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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

val SCROLLBAR_WIDTH = 10.dp

sealed class ScrollbarDimens {
  sealed class Vertical : ScrollbarDimens() {
    abstract val width: Int

    data class Dynamic(override val width: Int, val minHeight: Int) : Vertical()
    data class Static(override val width: Int, val height: Int) : Vertical()
  }

  sealed class Horizontal : ScrollbarDimens() {
    abstract val height: Int

    data class Dynamic(override val height: Int, val minWidth: Int) : Horizontal()
    data class Static(override val height: Int, val width: Int) : Horizontal()
  }
}

interface LazyStateWrapper {
  val isScrollInProgress: Boolean
  val firstVisibleItemIndex: Int?
  val visibleItemsCount: Int
  val fullyVisibleItemsCount: Int
  val totalItemsCount: Int
  val viewportHeight: Int

  suspend fun scrollToItem(index: Int, scrollOffset: Int = 0)
}

class LazyListStateWrapper(
  val lazyListState: LazyListState
) : LazyStateWrapper {

  override val isScrollInProgress: Boolean
    get() = lazyListState.isScrollInProgress
  override val firstVisibleItemIndex: Int?
    get() = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
  override val visibleItemsCount: Int
    get() = lazyListState.layoutInfo.visibleItemsInfo.size
  override val fullyVisibleItemsCount: Int
    get() = lazyListState.layoutInfo.visibleItemsInfo.count { lazyListItemInfo -> lazyListItemInfo.offset >= 0 }
  override val totalItemsCount: Int
    get() = lazyListState.layoutInfo.totalItemsCount
  override val viewportHeight: Int
    get() = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset

  override suspend fun scrollToItem(index: Int, scrollOffset: Int) {
    lazyListState.scrollToItem(index, scrollOffset)
  }

}

class LazyGridStateWrapper(
  val lazyGridState: LazyGridState
) : LazyStateWrapper {

  override val isScrollInProgress: Boolean
    get() = lazyGridState.isScrollInProgress
  override val firstVisibleItemIndex: Int?
    get() = lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
  override val visibleItemsCount: Int
    get() = lazyGridState.layoutInfo.visibleItemsInfo.size
  override val fullyVisibleItemsCount: Int
    get() = lazyGridState.layoutInfo.visibleItemsInfo.count { lazyListItemInfo -> lazyListItemInfo.offset.y >= 0 }
  override val totalItemsCount: Int
    get() = lazyGridState.layoutInfo.totalItemsCount
  override val viewportHeight: Int
    get() = lazyGridState.layoutInfo.viewportEndOffset - lazyGridState.layoutInfo.viewportStartOffset

  override suspend fun scrollToItem(index: Int, scrollOffset: Int) {
    lazyGridState.scrollToItem(index, scrollOffset)
  }

}

fun Modifier.scrollbar(
  state: LazyListState,
  scrollbarDimens: ScrollbarDimens,
  scrollbarTrackColor: Color? = null,
  scrollbarThumbColorNormal: Color? = null,
  scrollbarThumbColorDragged: Color? = null,
  contentPadding: PaddingValues = DefaultPaddingValues,
  scrollbarManualDragProgress: Float? = null,
  isScrollInProgress: (LazyStateWrapper) -> Boolean = { lazyListState -> lazyListState.isScrollInProgress }
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val lazyListStateWrapper = remember { LazyListStateWrapper(state) }

    return@composed scrollbar(
      lazyStateWrapper = lazyListStateWrapper,
      scrollbarDimens = scrollbarDimens,
      scrollbarTrackColor = scrollbarTrackColor ?: chanTheme.scrollbarTrackColorCompose,
      scrollbarThumbColorNormal = scrollbarThumbColorNormal ?: chanTheme.scrollbarThumbColorNormalCompose,
      scrollbarThumbColorDragged = scrollbarThumbColorDragged ?: chanTheme.scrollbarThumbColorDraggedCompose,
      contentPadding = contentPadding,
      scrollbarManualDragProgress = scrollbarManualDragProgress,
      isScrollInProgress = isScrollInProgress
    )
  }
}

fun Modifier.scrollbar(
  state: LazyGridState,
  scrollbarDimens: ScrollbarDimens,
  scrollbarTrackColor: Color? = null,
  scrollbarThumbColorNormal: Color? = null,
  scrollbarThumbColorDragged: Color? = null,
  contentPadding: PaddingValues = DefaultPaddingValues,
  scrollbarManualDragProgress: Float? = null,
  isScrollInProgress: (LazyStateWrapper) -> Boolean = { lazyListState -> lazyListState.isScrollInProgress }
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val lazyListStateWrapper = remember { LazyGridStateWrapper(state) }

    return@composed scrollbar(
      lazyStateWrapper = lazyListStateWrapper,
      scrollbarDimens = scrollbarDimens,
      scrollbarTrackColor = scrollbarTrackColor ?: chanTheme.scrollbarTrackColorCompose,
      scrollbarThumbColorNormal = scrollbarThumbColorNormal ?: chanTheme.scrollbarThumbColorNormalCompose,
      scrollbarThumbColorDragged = scrollbarThumbColorDragged ?: chanTheme.scrollbarThumbColorDraggedCompose,
      contentPadding = contentPadding,
      scrollbarManualDragProgress = scrollbarManualDragProgress,
      isScrollInProgress = isScrollInProgress
    )
  }
}

fun Modifier.scrollbar(
  lazyStateWrapper: LazyStateWrapper,
  scrollbarDimens: ScrollbarDimens,
  scrollbarTrackColor: Color,
  scrollbarThumbColorNormal: Color,
  scrollbarThumbColorDragged: Color,
  contentPadding: PaddingValues,
  scrollbarManualDragProgress: Float? = null,
  isScrollInProgress: (LazyStateWrapper) -> Boolean = { lazyListState -> lazyListState.isScrollInProgress }
): Modifier {
  return composed(
    inspectorInfo = debugInspectorInfo {
      name = "scrollbar"
      properties["scrollbarTrackColor"] = scrollbarTrackColor
      properties["scrollbarThumbColorNormal"] = scrollbarThumbColorNormal
      properties["scrollbarThumbColorDragged"] = scrollbarThumbColorDragged
      properties["contentPadding"] = contentPadding
      properties["scrollbarDimens"] = scrollbarDimens
      properties["scrollbarManualDragProgress"] = scrollbarManualDragProgress
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

      val isScrollbarDragged = scrollbarManualDragProgress != null
      val targetThumbAlpha = if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) 1f else 0f
      val targetTrackAlpha = if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) 0.7f else 0f
      val duration = if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) 150 else 1000
      val delay = if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) 0 else 1000

      val thumbAlphaAnimated by animateFloatAsState(
        targetValue = targetThumbAlpha,
        animationSpec = tween(
          durationMillis = duration,
          delayMillis = delay
        )
      )

      val trackAlphaAnimated by animateFloatAsState(
        targetValue = targetTrackAlpha,
        animationSpec = tween(
          durationMillis = duration,
          delayMillis = delay
        )
      )

      this.then(
        Modifier.drawWithContent {
          drawContent()

          val firstVisibleElementIndex = lazyStateWrapper.firstVisibleItemIndex
          val needDrawScrollbar = lazyStateWrapper.totalItemsCount > lazyStateWrapper.visibleItemsCount
            && (isScrollInProgress(lazyStateWrapper) || thumbAlphaAnimated > 0f || trackAlphaAnimated > 0f)

          // Draw scrollbar if total item count is greater than visible item count and either
          // currently scrolling or if the animation is still running and lazy column has content
          if (!needDrawScrollbar || firstVisibleElementIndex == null) {
            return@drawWithContent
          }

          when (scrollbarDimens) {
            is ScrollbarDimens.Horizontal -> {
              val (scrollbarOffsetX, scrollbarWidthAdjusted) = when (scrollbarDimens) {
                is ScrollbarDimens.Horizontal.Dynamic -> {
                  calculateDynamicScrollbarWidth(
                    leftPaddingPx = leftPaddingPx,
                    rightPaddingPx = rightPaddingPx,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarMinWidth = scrollbarDimens.minWidth.toFloat(),
                    realScrollbarWidthDiff = null
                  )
                }
                is ScrollbarDimens.Horizontal.Static -> {
                  calculateStaticScrollbarWidth(
                    leftPaddingPx = leftPaddingPx,
                    rightPaddingPx = rightPaddingPx,
                    scrollbarManualDragProgress = scrollbarManualDragProgress,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarWidth = scrollbarDimens.width.toFloat()
                  )
                }
              }

              val offsetX = leftPaddingPx + scrollbarOffsetX
              val offsetY = this.size.height - scrollbarDimens.height

              drawRect(
                color = scrollbarTrackColor,
                topLeft = Offset(leftPaddingPx, offsetY),
                size = Size(this.size.width - (leftPaddingPx + rightPaddingPx), scrollbarDimens.height.toFloat()),
                alpha = trackAlphaAnimated
              )

              val thumbColor = if (isScrollbarDragged) {
                scrollbarThumbColorDragged
              } else {
                scrollbarThumbColorNormal
              }

              drawRect(
                color = thumbColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(scrollbarWidthAdjusted, scrollbarDimens.height.toFloat()),
                alpha = thumbAlphaAnimated
              )
            }
            is ScrollbarDimens.Vertical -> {
              val (scrollbarOffsetY, scrollbarHeightAdjusted) = when (scrollbarDimens) {
                is ScrollbarDimens.Vertical.Dynamic -> {
                  calculateDynamicScrollbarHeight(
                    topPaddingPx = topPaddingPx,
                    bottomPaddingPx = bottomPaddingPx,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarMinHeight = scrollbarDimens.minHeight.toFloat(),
                    realScrollbarHeightDiff = null
                  )
                }
                is ScrollbarDimens.Vertical.Static -> {
                  calculateStaticScrollbarHeight(
                    topPaddingPx = topPaddingPx,
                    bottomPaddingPx = bottomPaddingPx,
                    scrollbarManualDragProgress = scrollbarManualDragProgress,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarHeight = scrollbarDimens.height.toFloat()
                  )
                }
              }

              val offsetY = topPaddingPx + scrollbarOffsetY
              val offsetX = this.size.width - scrollbarDimens.width

              drawRect(
                color = scrollbarTrackColor,
                topLeft = Offset(offsetX, topPaddingPx),
                size = Size(scrollbarDimens.width.toFloat(), this.size.height - (topPaddingPx + bottomPaddingPx)),
                alpha = trackAlphaAnimated
              )

              val thumbColor = if (isScrollbarDragged) {
                scrollbarThumbColorDragged
              } else {
                scrollbarThumbColorNormal
              }

              drawRect(
                color = thumbColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(scrollbarDimens.width.toFloat(), scrollbarHeightAdjusted),
                alpha = thumbAlphaAnimated
              )
            }
          }
        }
      )
    }
  )
}

private fun ContentDrawScope.calculateDynamicScrollbarWidth(
  leftPaddingPx: Float,
  rightPaddingPx: Float,
  lazyStateWrapper: LazyStateWrapper,
  firstVisibleElementIndex: Int,
  scrollbarMinWidth: Float,
  realScrollbarWidthDiff: Float?
): Pair<Float, Float> {
  val totalWidthWithoutPaddings = this.size.width - (realScrollbarWidthDiff ?: 0f) - leftPaddingPx - rightPaddingPx
  val elementWidth = totalWidthWithoutPaddings / lazyStateWrapper.totalItemsCount
  val scrollbarOffsetX = firstVisibleElementIndex * elementWidth
  val scrollbarWidthReal = (lazyStateWrapper.visibleItemsCount * elementWidth)
  val scrollbarWidthAdjusted = scrollbarWidthReal.coerceAtLeast(scrollbarMinWidth)

  if (scrollbarWidthAdjusted > scrollbarWidthReal && realScrollbarWidthDiff == null) {
    return calculateDynamicScrollbarWidth(
      leftPaddingPx = leftPaddingPx,
      rightPaddingPx = rightPaddingPx,
      lazyStateWrapper = lazyStateWrapper,
      firstVisibleElementIndex = firstVisibleElementIndex,
      scrollbarMinWidth = scrollbarMinWidth,
      realScrollbarWidthDiff = (scrollbarWidthAdjusted - scrollbarWidthReal)
    )
  }

  return Pair(scrollbarOffsetX, scrollbarWidthAdjusted)
}

private fun ContentDrawScope.calculateDynamicScrollbarHeight(
  topPaddingPx: Float,
  bottomPaddingPx: Float,
  lazyStateWrapper: LazyStateWrapper,
  firstVisibleElementIndex: Int,
  scrollbarMinHeight: Float,
  realScrollbarHeightDiff: Float?
): Pair<Float, Float> {
  val totalHeightWithoutPaddings = this.size.height - (realScrollbarHeightDiff ?: 0f) - topPaddingPx - bottomPaddingPx
  val elementHeight = totalHeightWithoutPaddings / lazyStateWrapper.totalItemsCount
  val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
  val scrollbarHeightReal = (lazyStateWrapper.visibleItemsCount * elementHeight)
  val scrollbarHeightAdjusted = scrollbarHeightReal.coerceAtLeast(scrollbarMinHeight)

  if (scrollbarHeightAdjusted > scrollbarHeightReal && realScrollbarHeightDiff == null) {
    return calculateDynamicScrollbarHeight(
      topPaddingPx = topPaddingPx,
      bottomPaddingPx = bottomPaddingPx,
      lazyStateWrapper = lazyStateWrapper,
      firstVisibleElementIndex = firstVisibleElementIndex,
      scrollbarMinHeight = scrollbarMinHeight,
      realScrollbarHeightDiff = (scrollbarHeightAdjusted - scrollbarHeightReal)
    )
  }

  return Pair(scrollbarOffsetY, scrollbarHeightAdjusted)
}

@Suppress("IfThenToElvis")
private fun ContentDrawScope.calculateStaticScrollbarWidth(
  leftPaddingPx: Float,
  rightPaddingPx: Float,
  scrollbarManualDragProgress: Float?,
  lazyStateWrapper: LazyStateWrapper,
  firstVisibleElementIndex: Int,
  scrollbarWidth: Float
): Pair<Float, Float> {
  val scrollProgress = if (scrollbarManualDragProgress == null) {
    firstVisibleElementIndex.toFloat() /
      (lazyStateWrapper.totalItemsCount.toFloat() - lazyStateWrapper.visibleItemsCount.toFloat())
  } else {
    scrollbarManualDragProgress
  }

  val totalWidth = this.size.width - scrollbarWidth - leftPaddingPx - rightPaddingPx
  val scrollbarOffsetX = (scrollProgress * totalWidth)

  return Pair(scrollbarOffsetX, scrollbarWidth)
}

@Suppress("IfThenToElvis")
private fun ContentDrawScope.calculateStaticScrollbarHeight(
  topPaddingPx: Float,
  bottomPaddingPx: Float,
  scrollbarManualDragProgress: Float?,
  lazyStateWrapper: LazyStateWrapper,
  firstVisibleElementIndex: Int,
  scrollbarHeight: Float
): Pair<Float, Float> {
  val scrollProgress = if (scrollbarManualDragProgress == null) {
    firstVisibleElementIndex.toFloat() /
      (lazyStateWrapper.totalItemsCount.toFloat() - lazyStateWrapper.visibleItemsCount.toFloat())
  } else {
    scrollbarManualDragProgress
  }

  val totalHeight = this.size.height - scrollbarHeight - topPaddingPx - bottomPaddingPx
  val scrollbarOffsetY = (scrollProgress * totalHeight)

  return Pair(scrollbarOffsetY, scrollbarHeight)
}