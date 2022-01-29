package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.view.View
import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import logcat.logcat

val LocalWindowInsets = compositionLocalOf<Insets> { error("Not initialized") }

@Composable
fun ProvideWindowInsets(
  window: Window,
  content: @Composable () -> Unit
) {
  val density = LocalDensity.current
  val view = LocalView.current
  var insetsRect by remember { mutableStateOf(Insets(density, Rect(Offset.Zero, Offset.Zero))) }

  DisposableEffect(
    key1 = view,
    effect = {
      val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
          v.requestApplyInsets()
        }

        override fun onViewDetachedFromWindow(v: View) {

        }
      }

      // TODO(KurobaEx): this is currently a weird bug where this method is not called sometimes on
      //  Android 29 (and 27, probably every Android < 31) when it is called on Android 31.
      //  For example when a dialog screen is opened with a TextField, when clicking the field
      //  the keyboard shows up but this method is not called so we never update the insets.
      //  However longtapping the TextField causes this method to get called.
      val listener = OnApplyWindowInsetsListener { view, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

        val left = Math.max(imeInsets.left, insets.systemWindowInsetLeft).toFloat()
        val top = Math.max(imeInsets.top, insets.systemWindowInsetTop).toFloat()
        val right = Math.max(imeInsets.right, insets.systemWindowInsetRight).toFloat()
        val bottom = Math.max(imeInsets.bottom, insets.systemWindowInsetBottom).toFloat()
        val newInsetsRect = Rect(left, top, right, bottom)

        insetsRect = Insets(density, newInsetsRect)
        logcat { "onApplyWindowInsets() called, newInsets=${insetsRect}" }

        return@OnApplyWindowInsetsListener ViewCompat.onApplyWindowInsets(
          view,
          insets.replaceSystemWindowInsets(0, 0, 0, 0)
        )
      }

      ViewCompat.setOnApplyWindowInsetsListener(window.decorView, listener)
      ViewCompat.requestApplyInsets(window.decorView)
      window.decorView.addOnAttachStateChangeListener(attachListener)

      onDispose {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView, null)
        window.decorView.removeOnAttachStateChangeListener(attachListener)
      }
    }
  )

  CompositionLocalProvider(LocalWindowInsets provides insetsRect) {
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