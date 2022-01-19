package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat

val LocalWindowInsets = staticCompositionLocalOf<Insets> { error("Not initialized") }

@Composable
fun ProvideWindowInsets(
  window: Window,
  content: @Composable () -> Unit
) {
  val density = LocalDensity.current
  val insetsRect = remember { mutableStateOf(Insets(density, Rect(Offset.Zero, Offset.Zero))) }

  DisposableEffect(key1 = Unit) {
    val listener = OnApplyWindowInsetsListener { view, insets ->
      val rect = Rect(
        insets.systemWindowInsetLeft.toFloat(),
        insets.systemWindowInsetTop.toFloat(),
        insets.systemWindowInsetRight.toFloat(),
        insets.systemWindowInsetBottom.toFloat()
      )

      insetsRect.value = Insets(density, rect)

      ViewCompat.onApplyWindowInsets(
        view,
        insets.replaceSystemWindowInsets(0, 0, 0, 0)
      )
    }

    ViewCompat.setOnApplyWindowInsetsListener(window.decorView, listener)

    onDispose {
      ViewCompat.setOnApplyWindowInsetsListener(window.decorView, null)
    }
  }

  CompositionLocalProvider(LocalWindowInsets provides insetsRect.value) {
    content()
  }

}

class Insets(
  private val density: Density,
  val insetsRect: Rect
) {
  val leftDp: Dp
    get() = with(density) { insetsRect.left.toDp() }
  val rightDp: Dp
    get() = with(density) { insetsRect.right.toDp() }
  val topDp: Dp
    get() = with(density) { insetsRect.top.toDp() }
  val bottomDp: Dp
    get() = with(density) { insetsRect.bottom.toDp() }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Insets

    if (insetsRect != other.insetsRect) return false

    return true
  }

  override fun hashCode(): Int {
    return insetsRect.hashCode()
  }

  override fun toString(): String {
    return "Insets(insetsRect=$insetsRect)"
  }

}