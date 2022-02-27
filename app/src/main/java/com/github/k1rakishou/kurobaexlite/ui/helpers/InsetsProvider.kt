package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

val LocalWindowInsets = compositionLocalOf<Insets> { error("Not initialized") }

@Composable
fun ProvideWindowInsets(
  window: Window,
  content: @Composable () -> Unit
) {
  val density = LocalDensity.current
  val view = LocalView.current
  var currentInsets by remember { mutableStateOf(Insets(density, 0.dp, 0.dp, 0.dp, 0.dp)) }

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
      //  Android 29 (and 27, probably every Android < 31) however it is always called on Android 31.
      //  For example when a dialog screen is opened with a TextField, when clicking the field
      //  the keyboard shows up but this method is not called so we never update the insets.
      //  However longtapping the TextField causes this method to get called.
      val listener = OnApplyWindowInsetsListener { view, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

        val left = with(density) { Math.max(imeInsets.left, insets.systemWindowInsetLeft).toFloat().toDp() }
        val top = with(density) { Math.max(imeInsets.top, insets.systemWindowInsetTop).toFloat().toDp() }
        val right = with(density) { Math.max(imeInsets.right, insets.systemWindowInsetRight).toFloat().toDp() }
        val bottom = with(density) { Math.max(imeInsets.bottom, insets.systemWindowInsetBottom).toFloat().toDp() }

        currentInsets = Insets(
          density = density,
          left = left,
          right = right,
          top = top,
          bottom = bottom
        )

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

  CompositionLocalProvider(LocalWindowInsets provides currentInsets) {
    content()
  }
}

class Insets(
  private val density: Density,
  val left: Dp,
  val right: Dp,
  val top: Dp,
  val bottom: Dp
) {

  fun asPaddingValues(
    consumeLeft: Boolean = false,
    consumeRight: Boolean = false,
    consumeTop: Boolean = false,
    consumeBottom: Boolean = false,
  ): PaddingValues {
    return PaddingValues(
      start = left.takeUnless { consumeLeft } ?: 0.dp,
      end = right.takeUnless { consumeRight } ?: 0.dp,
      top = top.takeUnless { consumeTop } ?: 0.dp,
      bottom = bottom.takeUnless { consumeBottom } ?: 0.dp
    )
  }

  fun copy(
    newLeft: Dp = left,
    newRight: Dp = right,
    newTop: Dp = top,
    newBottom: Dp = bottom,
  ): Insets {
    return Insets(density, newLeft, newRight, newTop, newBottom)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Insets

    if (density != other.density) return false
    if (left != other.left) return false
    if (right != other.right) return false
    if (top != other.top) return false
    if (bottom != other.bottom) return false

    return true
  }

  override fun hashCode(): Int {
    var result = density.hashCode()
    result = 31 * result + left.hashCode()
    result = 31 * result + right.hashCode()
    result = 31 * result + top.hashCode()
    result = 31 * result + bottom.hashCode()
    return result
  }

  override fun toString(): String {
    return "Insets(leftDp=$left, rightDp=$right, topDp=$top, bottomDp=$bottom)"
  }

}