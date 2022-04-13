package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Taken from https://github.com/savvasdalkitsis/lazy-staggered-grid/blob/main/lazystaggeredgrid/src/main/kotlin/com/savvasdalkitsis/lazystaggeredgrid/LazyStaggeredGrid.kt
 * */

class LazyStaggeredGridState(
  val columnCount: Int,
  val initialIndex: Int
) {
  @Volatile private var states: Array<LazyListState>? = null

  suspend fun scrollToItem(index: Int) {
    if (states == null) {
      return
    }

    for (state in states!!) {
      state.scrollToItem(index / columnCount)
    }
  }

  @Composable
  fun states(): Array<LazyListState> {
    if (states != null) {
      return states!!
    }

    val newStates: Array<LazyListState> = (0 until columnCount)
      .map { rememberLazyListState(initialFirstVisibleItemIndex = initialIndex / columnCount) }
      .toTypedArray()
    states = newStates

    return states!!
  }

}

@Composable
fun rememberLazyStaggeredGridState(
  columnCount: Int,
  initialIndex: Int
): LazyStaggeredGridState {
  return remember { LazyStaggeredGridState(columnCount, initialIndex) }
}

@Composable
fun LazyStaggeredGrid(
  modifier: Modifier = Modifier,
  lazyStaggeredGridState: LazyStaggeredGridState,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  content: @Composable LazyStaggeredGridScope.() -> Unit,
) {
  val columnCount = lazyStaggeredGridState.columnCount
  val states = lazyStaggeredGridState.states()

  val scope = rememberCoroutineScope { Dispatchers.Main.immediate }
  val scrollableState = rememberScrollableState { delta ->
    scope.launch { states.forEach { state -> state.scrollBy(-delta) } }
    return@rememberScrollableState delta
  }

  val density = LocalDensity.current
  val chanTheme = LocalChanTheme.current

  val scrollbarWidth = with(density) { 8.dp.roundToPx() }
  val scrollbarMinHeight = with(density) { 32.dp.roundToPx() }

  val gridScope = LazyStaggeredGridScope(columnCount)
  content(gridScope)

  Box(
    modifier = modifier
      .scrollable(
        state = scrollableState,
        orientation = Orientation.Vertical,
        flingBehavior = ScrollableDefaults.flingBehavior()
      )
  ) {
    Row {
      for (index in 0 until columnCount) {
        val state = states[index]

        val scrollbarModifier = if (index == columnCount - 1) {
          Modifier.scrollbar(
            state = state,
            scrollbarDimens = ScrollbarDimens.Vertical(scrollbarWidth, scrollbarMinHeight),
            thumbColor = chanTheme.textColorHintCompose,
            scrollbarDragged = false,
            isScrollInProgress = { lazyListState -> lazyListState.isScrollInProgress || scrollableState.isScrollInProgress }
          )
        } else {
          Modifier
        }

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .then(scrollbarModifier),
          userScrollEnabled = false,
          contentPadding = contentPadding,
          state = state
        ) {
          for (item in gridScope.items[index]) {
            item(key = item.key) {
              item.content()
            }
          }
        }
      }
    }
  }
}

class LazyStaggeredGridScope(
  private val columnCount: Int,
) {
  var currentIndex = 0
  val items: Array<MutableList<LazyStaggeredGridItem>> = Array(columnCount) { mutableListOf() }

  fun item(key: Any, content: @Composable () -> Unit) {
    items[currentIndex % columnCount] += LazyStaggeredGridItem(key, content)
    currentIndex += 1
  }
}

class LazyStaggeredGridItem(
  val key: Any,
  val content: @Composable () -> Unit
)