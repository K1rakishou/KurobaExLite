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
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LazyColumnWithFastScroller(
  modifier: Modifier = Modifier,
  lazyListState: LazyListState,
  contentPadding: PaddingValues,
  userScrollEnabled: Boolean = true,
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyListScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val scrollbarWidth = with(LocalDensity.current) {
    remember { SCROLLBAR_WIDTH.toPx().toInt() }
  }
  val scrollbarMinHeightPx = with(LocalDensity.current) {
    remember { SCROLLBAR_MIN_SIZE.toPx().toInt() }
  }
  val paddingTopPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateTopPadding().toPx().toInt() }
  }
  val paddingBottomPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateBottomPadding().toPx().toInt() }
  }
  val thumbColor = chanTheme.textColorHintCompose
  val lazyListStateWrapper = remember { LazyListStateWrapper(lazyListState) }

  val coroutineScope = rememberCoroutineScope()
  var scrollbarDragged by remember { mutableStateOf(false) }

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
              lazyStateWrapper = lazyListStateWrapper,
              width = maxWidthPx,
              height = maxHeightPx,
              paddingTop = paddingTopPx,
              paddingBottom = paddingBottomPx,
              scrollbarWidth = scrollbarWidth,
              onScrollbarDragStateUpdated = { dragging ->
                scrollbarDragged = dragging
                onFastScrollerDragStateChanged?.invoke(dragging)
              }
            )
          }
        )
    ) {
      LazyColumn(
        modifier = Modifier
          .scrollbar(
            lazyStateWrapper = lazyListStateWrapper,
            thumbColor = thumbColor,
            scrollbarDimens = ScrollbarDimens.Vertical(
              width = scrollbarWidth,
              minHeight = scrollbarMinHeightPx
            ),
            contentPadding = contentPadding,
            isScrollbarDragged = scrollbarDragged
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
  columns: GridCells,
  lazyGridState: LazyGridState,
  contentPadding: PaddingValues,
  userScrollEnabled: Boolean = true,
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyGridScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val scrollbarWidth = with(LocalDensity.current) {
    remember { SCROLLBAR_WIDTH.toPx().toInt() }
  }
  val scrollbarMinHeightPx = with(LocalDensity.current) {
    remember { SCROLLBAR_MIN_SIZE.toPx().toInt() }
  }
  val paddingTopPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateTopPadding().toPx().toInt() }
  }
  val paddingBottomPx = with(LocalDensity.current) {
    remember(contentPadding) { contentPadding.calculateBottomPadding().toPx().toInt() }
  }
  val thumbColor = chanTheme.textColorHintCompose
  val lazyGridStateWrapper = remember { LazyGridStateWrapper(lazyGridState) }

  val coroutineScope = rememberCoroutineScope()
  var scrollbarDragged by remember { mutableStateOf(false) }

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
              height = maxHeightPx,
              paddingTop = paddingTopPx,
              paddingBottom = paddingBottomPx,
              scrollbarWidth = scrollbarWidth,
              onScrollbarDragStateUpdated = { dragging ->
                scrollbarDragged = dragging
                onFastScrollerDragStateChanged?.invoke(dragging)
              }
            )
          }
        )
    ) {
      LazyVerticalGrid(
        modifier = Modifier
          .scrollbar(
            lazyStateWrapper = lazyGridStateWrapper,
            thumbColor = thumbColor,
            scrollbarDimens = ScrollbarDimens.Vertical(
              width = scrollbarWidth,
              minHeight = scrollbarMinHeightPx
            ),
            contentPadding = contentPadding,
            isScrollbarDragged = scrollbarDragged
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
  height: Int,
  paddingTop: Int,
  paddingBottom: Int,
  scrollbarWidth: Int,
  onScrollbarDragStateUpdated: (Boolean) -> Unit
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

      down.consumeAllChanges()

      try {
        onScrollbarDragStateUpdated(true)

        while (true) {
          val nextEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
          if (nextEvent.type != PointerEventType.Move) {
            break
          }

          for (change in nextEvent.changes) {
            change.consumeAllChanges()
          }

          nextEvent.changes.lastOrNull()?.let { lastChange ->
            coroutineScope.launch {
              val heightWithoutPaddings = height - paddingTop - paddingBottom
              val touchFraction = ((lastChange.position.y - paddingTop) / heightWithoutPaddings).coerceIn(0f, 1f)
              val scrollToIndex = (lazyStateWrapper.totalItemsCount.toFloat() * touchFraction).toInt()

              lazyStateWrapper.scrollToItem(scrollToIndex)
            }
          }
        }
      } finally {
        onScrollbarDragStateUpdated(false)
      }
    }
  }
}
