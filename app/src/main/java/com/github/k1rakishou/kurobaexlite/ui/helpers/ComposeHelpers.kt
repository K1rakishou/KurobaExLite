package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.IntSize

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