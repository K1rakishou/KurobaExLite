package com.github.k1rakishou.kurobaexlite.ui.helpers

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
  if (defaultFontSize.isUnspecified) {
    return defaultFontSize
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

  return (defaultFontSize * globalFontSizeMultiplier)
}

fun TextUnit.coerceIn(min: TextUnit? = null, max: TextUnit? = null): TextUnit {
  var textUnit = this

  if (min != null && textUnit < min) {
    textUnit = min
  }

  if (max != null && textUnit > max) {
    textUnit = max
  }

  return textUnit
}