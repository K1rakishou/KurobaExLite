package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.ui.unit.IntSize

fun IntSize.isZero(): Boolean {
  return width <= 0 && height <= 0
}