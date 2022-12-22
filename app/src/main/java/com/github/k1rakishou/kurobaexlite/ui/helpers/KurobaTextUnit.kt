package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit

@Composable
fun rememberKurobaTextUnit(
  fontSize: TextUnit,
  min: TextUnit? = null,
  max: TextUnit? = null
): KurobaTextUnit {
  return remember(fontSize, min, max) {
    KurobaTextUnit(fontSize, min, max)
  }
}

data class KurobaTextUnit(
  val fontSize: TextUnit,
  val min: TextUnit? = null,
  val max: TextUnit? = null
)