package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val SCROLLBAR_WIDTH = 10.dp
private val SCROLLBAR_MIN_HEIGHT = 36.dp

@Composable
fun LazyColumnWithFastScroller(
  modifier: Modifier = Modifier,
  lazyListState: LazyListState = rememberLazyListState(),
  contentPadding: PaddingValues = PaddingValues(),
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyListScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val scrollbarWidth = with(LocalDensity.current) { SCROLLBAR_WIDTH.toPx() }
  val scrollbarMinHeightPx = with(LocalDensity.current) { SCROLLBAR_MIN_HEIGHT.toPx() }
  val paddingTopPx = with(LocalDensity.current) { contentPadding.calculateTopPadding().toPx() }
  val paddingBottomPx = with(LocalDensity.current) { contentPadding.calculateBottomPadding().toPx() }
  val coroutineScope = rememberCoroutineScope()
  var scrollbarDragged by remember { mutableStateOf(false) }

  BoxWithConstraints(modifier = modifier) {
    val maxWidthPx = with(LocalDensity.current) { remember(key1 = maxWidth) { maxWidth.toPx() } }
    val maxHeightPx = with(LocalDensity.current) { remember(key1 = maxHeight) { maxHeight.toPx() } }

    Box(
      modifier = Modifier
        .pointerInput(
          key1 = Unit,
          block = {
            processFastScrollerInputs(
              coroutineScope = coroutineScope,
              lazyListState = lazyListState,
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
          .simpleVerticalScrollbar(
            state = lazyListState,
            chanTheme = chanTheme,
            contentPadding = contentPadding,
            scrollbarWidth = scrollbarWidth,
            scrollbarMinHeight = scrollbarMinHeightPx,
            scrollbarDragged = scrollbarDragged
          ),
        state = lazyListState,
        contentPadding = contentPadding,
        content = content
      )
    }
  }

}

suspend fun PointerInputScope.processFastScrollerInputs(
  coroutineScope: CoroutineScope,
  lazyListState: LazyListState,
  width: Float,
  height: Float,
  paddingTop: Float,
  paddingBottom: Float,
  scrollbarWidth: Float,
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
              val scrollToIndex = (lazyListState.layoutInfo.totalItemsCount.toFloat() * touchFraction).toInt()

              lazyListState.scrollToItem(scrollToIndex)
            }
          }
        }
      } finally {
        onScrollbarDragStateUpdated(false)
      }
    }
  }
}
