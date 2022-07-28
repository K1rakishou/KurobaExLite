package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.managers.FastScrollerMarksManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyGridStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyListStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.ScrollbarDimens
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.scrollbar
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val DEFAULT_SCROLLBAR_WIDTH = 10.dp
val DEFAULT_SCROLLBAR_HEIGHT = 64.dp

@Composable
private fun DefaultScrollbarWidth(): Int {
  return with(LocalDensity.current) { remember { DEFAULT_SCROLLBAR_WIDTH.toPx().toInt() } }
}

@Composable
private fun DefaultScrollbarHeight(): Int {
  return with(LocalDensity.current) { remember { DEFAULT_SCROLLBAR_HEIGHT.toPx().toInt() } }
}

@Composable
fun LazyColumnWithFastScroller(
  lazyListContainerModifier: Modifier = Modifier,
  lazyListModifier: Modifier = Modifier,
  scrollbarWidth: Int = DefaultScrollbarWidth(),
  scrollbarHeight: Int = DefaultScrollbarHeight(),
  lazyListState: LazyListState,
  contentPadding: PaddingValues,
  fastScrollerMarks: FastScrollerMarksManager.FastScrollerMarks? = null,
  userScrollEnabled: Boolean = true,
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyListScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val paddingTopPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateTopPadding().toPx().toInt() }
  }
  val paddingBottomPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateBottomPadding().toPx().toInt() }
  }
  val lazyListStateWrapper = remember { LazyListStateWrapper(lazyListState) }
  val coroutineScope = rememberCoroutineScope()

  var scrollbarDragProgress by remember { mutableStateOf<Float?>(null) }

  BoxWithConstraints(modifier = lazyListContainerModifier) {
    val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
    val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }

    Box(
      modifier = Modifier
        .pointerInput(
          contentPadding,
          maxWidthPx,
          maxHeightPx,
          block = {
            processFastScrollerInputs(
              coroutineScope = coroutineScope,
              lazyStateWrapper = lazyListStateWrapper,
              width = maxWidthPx,
              paddingTop = paddingTopPx,
              paddingBottom = paddingBottomPx,
              scrollbarWidth = scrollbarWidth,
              onScrollbarDragStateUpdated = { dragProgress ->
                scrollbarDragProgress = dragProgress
                onFastScrollerDragStateChanged?.invoke(dragProgress != null)
              }
            )
          }
        )
    ) {
      LazyColumn(
        modifier = Modifier
          .then(lazyListModifier)
          .scrollbar(
            lazyStateWrapper = lazyListStateWrapper,
            scrollbarDimens = ScrollbarDimens.Vertical.Static(
              width = scrollbarWidth,
              height = scrollbarHeight
            ),
            scrollbarTrackColor = chanTheme.scrollbarTrackColor,
            scrollbarThumbColorNormal = chanTheme.scrollbarThumbColorNormal,
            scrollbarThumbColorDragged = chanTheme.scrollbarThumbColorDragged,
            contentPadding = contentPadding,
            fastScrollerMarks = fastScrollerMarks,
            scrollbarManualDragProgress = scrollbarDragProgress
          ),
        userScrollEnabled = userScrollEnabled,
        state = lazyListState,
        contentPadding = contentPadding,
        content = content
      )
    }
  }

}

@Composable
fun LazyVerticalGridWithFastScroller(
  modifier: Modifier = Modifier,
  scrollbarWidth: Int = DefaultScrollbarWidth(),
  scrollbarHeight: Int = DefaultScrollbarHeight(),
  columns: GridCells,
  lazyGridState: LazyGridState,
  contentPadding: PaddingValues,
  userScrollEnabled: Boolean = true,
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyGridScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val paddingTopPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateTopPadding().toPx().toInt() }
  }
  val paddingBottomPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateBottomPadding().toPx().toInt() }
  }
  val lazyGridStateWrapper = remember { LazyGridStateWrapper(lazyGridState) }
  val coroutineScope = rememberCoroutineScope()

  var scrollbarManualDragProgress by remember { mutableStateOf<Float?>(null) }

  BoxWithConstraints(modifier = modifier) {
    val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
    val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }

    Box(
      modifier = Modifier
        .pointerInput(
          contentPadding,
          maxWidthPx,
          maxHeightPx,
          block = {
            processFastScrollerInputs(
              coroutineScope = coroutineScope,
              lazyStateWrapper = lazyGridStateWrapper,
              width = maxWidthPx,
              paddingTop = paddingTopPx,
              paddingBottom = paddingBottomPx,
              scrollbarWidth = scrollbarWidth,
              onScrollbarDragStateUpdated = { dragProgress ->
                scrollbarManualDragProgress = dragProgress
                onFastScrollerDragStateChanged?.invoke(dragProgress != null)
              }
            )
          }
        )
    ) {
      LazyVerticalGrid(
        modifier = Modifier
          .scrollbar(
            lazyStateWrapper = lazyGridStateWrapper,
            scrollbarDimens = ScrollbarDimens.Vertical.Static(
              width = scrollbarWidth,
              height = scrollbarHeight
            ),
            scrollbarTrackColor = chanTheme.scrollbarTrackColor,
            scrollbarThumbColorNormal = chanTheme.scrollbarThumbColorNormal,
            scrollbarThumbColorDragged = chanTheme.scrollbarThumbColorDragged,
            contentPadding = contentPadding,
            scrollbarManualDragProgress = scrollbarManualDragProgress
          ),
        columns = columns,
        userScrollEnabled = userScrollEnabled,
        state = lazyGridStateWrapper.lazyGridState,
        contentPadding = contentPadding,
        content = content
      )
    }
  }
}

suspend fun PointerInputScope.processFastScrollerInputs(
  coroutineScope: CoroutineScope,
  lazyStateWrapper: LazyStateWrapper,
  width: Int,
  paddingTop: Int,
  paddingBottom: Int,
  scrollbarWidth: Int,
  onScrollbarDragStateUpdated: (Float?) -> Unit
) {
  forEachGesture {
    awaitPointerEventScope {
      val downEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
      if (downEvent.type != PointerEventType.Press) {
        return@awaitPointerEventScope
      }

      val down = downEvent.changes.firstOrNull()
        ?: return@awaitPointerEventScope

      if (down.position.x < (width - scrollbarWidth)) {
        return@awaitPointerEventScope
      }

      down.consume()

      var job: Job? = null

      try {
        while (true) {
          val nextEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
          if (nextEvent.type != PointerEventType.Move) {
            break
          }

          for (change in nextEvent.changes) {
            change.consume()
          }

          nextEvent.changes.lastOrNull()?.let { lastChange ->
            job = coroutineScope.launch {
              val touchY = lastChange.position.y - paddingTop
              val scrollbarTrackHeight = lazyStateWrapper.viewportHeight - paddingBottom - paddingTop

              val touchFraction = (touchY / scrollbarTrackHeight).coerceIn(0f, 1f)
              val itemsCount = (lazyStateWrapper.totalItemsCount - lazyStateWrapper.fullyVisibleItemsCount)

              var scrollToIndex = (itemsCount.toFloat() * touchFraction).roundToInt()
              if (touchFraction == 0f) {
                scrollToIndex = 0
              } else if (touchFraction == 1f) {
                // We want to use the actual last item index for scrolling when touchFraction == 1f
                // because otherwise we may end up not at the very bottom of the list but slightly
                // above it (like 1 element's height)
                scrollToIndex = lazyStateWrapper.totalItemsCount
              }

              lazyStateWrapper.scrollToItem(scrollToIndex)

              if (isActive) {
                onScrollbarDragStateUpdated(touchFraction)
              }
            }
          }
        }
      } finally {
        // Make sure the coroutine doesn't overwrite the onScrollbarDragStateUpdated() with non-null
        // value because otherwise the scrollbar will stuck in "dragging" state.
        job?.cancel()
        job = null

        onScrollbarDragStateUpdated(null)
      }
    }
  }
}
