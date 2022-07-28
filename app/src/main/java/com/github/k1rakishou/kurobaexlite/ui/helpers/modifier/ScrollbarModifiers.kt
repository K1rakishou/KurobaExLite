package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.managers.FastScrollerMarksManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme

private val DefaultPaddingValues = PaddingValues(0.dp)

@Immutable
sealed class ScrollbarDimens {

  @Immutable
  sealed class Vertical : ScrollbarDimens() {
    abstract val width: Int

    data class Dynamic(override val width: Int, val minHeight: Int) : Vertical()
    data class Static(override val width: Int, val height: Int) : Vertical()
  }

  @Immutable
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
      scrollbarTrackColor = scrollbarTrackColor ?: chanTheme.scrollbarTrackColor,
      scrollbarThumbColorNormal = scrollbarThumbColorNormal ?: chanTheme.scrollbarThumbColorNormal,
      scrollbarThumbColorDragged = scrollbarThumbColorDragged ?: chanTheme.scrollbarThumbColorDragged,
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
      scrollbarTrackColor = scrollbarTrackColor ?: chanTheme.scrollbarTrackColor,
      scrollbarThumbColorNormal = scrollbarThumbColorNormal ?: chanTheme.scrollbarThumbColorNormal,
      scrollbarThumbColorDragged = scrollbarThumbColorDragged ?: chanTheme.scrollbarThumbColorDragged,
      contentPadding = contentPadding,
      scrollbarManualDragProgress = scrollbarManualDragProgress,
      isScrollInProgress = isScrollInProgress
    )
  }
}

/**
 * scrollbar for LazyLists that use ScrollState (like verticalScroll())
 * */
fun Modifier.scrollbar(
  lazyStateWrapper: LazyStateWrapper,
  scrollbarDimens: ScrollbarDimens,
  scrollbarTrackColor: Color,
  scrollbarThumbColorNormal: Color,
  scrollbarThumbColorDragged: Color,
  contentPadding: PaddingValues,
  fastScrollerMarks: FastScrollerMarksManager.FastScrollerMarks? = null,
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
      val targetThumbAlpha = when {
        isScrollbarDragged -> 1f
        isScrollInProgress(lazyStateWrapper) -> 0.8f
        else -> 0f
      }

      val targetTrackAlpha = when {
        isScrollbarDragged -> 0.7f
        isScrollInProgress(lazyStateWrapper) -> 0.5f
        else -> 0f
      }

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

      val thumbColorAnimated by animateColorAsState(
        targetValue = if (isScrollbarDragged) scrollbarThumbColorDragged else scrollbarThumbColorNormal,
        animationSpec = tween(durationMillis = 200)
      )

      val minLabelHeight = with(density) { remember { 1.dp.toPx() } }

      this.then(
        Modifier.drawWithContent {
          drawContent()

          val firstVisibleElementIndex = lazyStateWrapper.firstVisibleItemIndex
          val needDrawScrollbar = lazyStateWrapper.totalItemsCount > lazyStateWrapper.visibleItemsCount
            && (isScrollInProgress(lazyStateWrapper) || thumbAlphaAnimated > 0f || trackAlphaAnimated > 0f)

          // Draw scrollbar if total item count is greater than visible item count and either
          // currently scrolling or if any of the animations is still running and lazy column has content
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

              // TODO(KurobaEx): fastScrollerMarks are not supported for horizontal scrollbars

              drawRect(
                color = thumbColorAnimated,
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

              kotlin.run {
                val trackWidth = scrollbarDimens.width.toFloat()
                val trackHeight = this.size.height - (topPaddingPx + bottomPaddingPx)

                val topLeft = Offset(offsetX, topPaddingPx)
                val size = Size(trackWidth, trackHeight)

                drawRect(
                  color = scrollbarTrackColor,
                  topLeft = topLeft,
                  size = size,
                  alpha = trackAlphaAnimated
                )

                if (fastScrollerMarks != null) {
                  val unit = trackHeight / lazyStateWrapper.totalItemsCount.toFloat()
                  val halfUnit = unit / 2f

                  val markLeft = this.size.width - trackWidth
                  val markRight = this.size.width

                  translate(top = topLeft.y) {
                    for (fastScrollerMark in fastScrollerMarks.marks) {
                      val color = fastScrollerMark.type.color
                      val startPosition = fastScrollerMark.startPosition
                      val endPosition = fastScrollerMark.endPosition

                      var top = startPosition * unit - halfUnit
                      var bottom = (endPosition * unit) + halfUnit

                      if (bottom - top < minLabelHeight) {
                        top -= minLabelHeight / 2f
                        bottom += minLabelHeight / 2f
                      }

                      drawRect(
                        color = color,
                        topLeft = Offset(markLeft, top),
                        size = Size(markRight - markLeft, bottom - top),
                        alpha = thumbAlphaAnimated
                      )
                    }
                  }
                }
              }

              kotlin.run {
                drawRect(
                  color = thumbColorAnimated,
                  topLeft = Offset(offsetX, offsetY),
                  size = Size(scrollbarDimens.width.toFloat(), scrollbarHeightAdjusted),
                  alpha = thumbAlphaAnimated
                )
              }
            }
          }
        }
      )
    }
  )
}

/**
 * Vertical scrollbar for Composables that use ScrollState (like verticalScroll())
 * */
fun Modifier.verticalScrollbar(
  contentPadding: PaddingValues,
  scrollState: ScrollState
): Modifier {
  return composed {
    val density = LocalDensity.current
    val chanTheme = LocalChanTheme.current

    val scrollbarWidth = with(density) { 4.dp.toPx() }
    val scrollbarHeight = with(density) { 16.dp.toPx() }
    val thumbColor = chanTheme.scrollbarThumbColorDragged

    val currentValue by remember { derivedStateOf { scrollState.value } }
    val maxValue by remember { derivedStateOf {  scrollState.maxValue } }

    val topPaddingPx = with(density) {
      remember(key1 = contentPadding) { contentPadding.calculateTopPadding().toPx() }
    }
    val bottomPaddingPx = with(density) {
      remember(key1 = contentPadding) { contentPadding.calculateBottomPadding().toPx() }
    }

    val duration = if (scrollState.isScrollInProgress) 150 else 1000
    val delay = if (scrollState.isScrollInProgress) 0 else 1000
    val targetThumbAlpha = if (scrollState.isScrollInProgress) 0.8f else 0f

    val thumbAlphaAnimated by animateFloatAsState(
      targetValue = targetThumbAlpha,
      animationSpec = tween(
        durationMillis = duration,
        delayMillis = delay
      )
    )

    return@composed Modifier.drawWithContent {
      drawContent()

      if (maxValue == Int.MAX_VALUE || maxValue == 0) {
        return@drawWithContent
      }

      val availableHeight = this.size.height - scrollbarHeight - topPaddingPx - bottomPaddingPx
      if (availableHeight > maxValue) {
        return@drawWithContent
      }

      val unit = availableHeight / maxValue.toFloat()
      val scrollPosition = currentValue * unit

      val offsetX = this.size.width - scrollbarWidth
      val offsetY = topPaddingPx + scrollPosition

      drawRect(
        color = thumbColor,
        topLeft = Offset(offsetX, offsetY),
        size = Size(scrollbarWidth, scrollbarHeight),
        alpha = thumbAlphaAnimated
      )
    }
  }
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