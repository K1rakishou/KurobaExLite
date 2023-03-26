package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun IntSize.isZero(): Boolean {
  return width <= 0 && height <= 0
}

/**
 * rememberSaveable() that resets it's state when [key1] changes
 * */
@Composable
fun <T : Any> rememberSaveableResettable(key1: Any, init: () -> T): T {
  return key(key1) { rememberSaveable(init = init) }
}

@Composable
fun collectTextFontSize(defaultFontSize: TextUnit): TextUnit {
  val defaultFontSizeKurobaUnits = rememberKurobaTextUnit(fontSize = defaultFontSize)

  return collectTextFontSize(defaultFontSize = defaultFontSizeKurobaUnits)
}

@Composable
fun collectTextFontSize(defaultFontSize: KurobaTextUnit): TextUnit {
  val textUnit = defaultFontSize.fontSize
  val min = defaultFontSize.min
  val max = defaultFontSize.max

  if (textUnit.isUnspecified) {
    return textUnit
  }

  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val coroutineScope = rememberCoroutineScope()

  var globalFontSizeMultiplier by remember { mutableStateOf(globalUiInfoManager.globalFontSizeMultiplier.value / 100f) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      coroutineScope.launch {
        globalUiInfoManager.globalFontSizeMultiplier
          .collectLatest { value -> globalFontSizeMultiplier = value / 100f }
      }
    }
  )

  var updatedTextUnit = (textUnit * globalFontSizeMultiplier)

  if (min != null && updatedTextUnit < min) {
    updatedTextUnit = min
  }

  if (max != null && updatedTextUnit > max) {
    updatedTextUnit = max
  }

  return updatedTextUnit
}

fun TextUnit.coerceIn(min: TextUnit? = null, max: TextUnit? = null): KurobaTextUnit {
  return KurobaTextUnit(fontSize = this, min = min, max = max)
}

@OptIn(ExperimentalFoundationApi::class)
suspend fun LazyStaggeredGridState.scrollToItemConsumeCancellation(
  index: Int,
  scrollOffset: Int = 0
) {
  try {
    scrollToItem(index, scrollOffset)
  } catch (error: CancellationException) {
    // no-op
    // Just consume the exception. We want this in cases when we scroll from inside of a flow collection method.
    // The scrolling can fail and it may throw CancellationException which will also cancel the flow collection.
    // We don't want that and that's why this method exists
  }
}

suspend fun LazyGridState.scrollToItemConsumeCancellation(
  index: Int,
  scrollOffset: Int = 0
) {
  try {
    scrollToItem(index, scrollOffset)
  } catch (error: CancellationException) {
    // no-op
    // Just consume the exception. We want this in cases when we scroll from inside of a flow collection method.
    // The scrolling can fail and it may throw CancellationException which will also cancel the flow collection.
    // We don't want that and that's why this method exists
  }
}

suspend fun LazyListState.scrollToItemConsumeCancellation(
  index: Int,
  scrollOffset: Int = 0
) {
  try {
    scrollToItem(index, scrollOffset)
  } catch (error: CancellationException) {
    // no-op
    // Just consume the exception. We want this in cases when we scroll from inside of a flow collection method.
    // The scrolling can fail and it may throw CancellationException which will also cancel the flow collection.
    // We don't want that and that's why this method exists
  }
}