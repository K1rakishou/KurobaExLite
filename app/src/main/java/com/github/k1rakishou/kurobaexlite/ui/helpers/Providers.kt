package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.k1rakishou.kurobaexlite.helpers.RuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine

val LocalChanTheme = staticCompositionLocalOf<ChanTheme> { error("Theme not provided") }
val LocalComponentActivity = staticCompositionLocalOf<ComponentActivity> { error("ComponentActivity not provided") }
val LocalWindowInsets = compositionLocalOf<Insets> { error("Not initialized") }
val LocalRuntimePermissionsHelper = compositionLocalOf<RuntimePermissionsHelper> { error("Not initialized") }

@Composable
fun ProvideAllTheStuff(
  componentActivity: ComponentActivity,
  window: Window,
  themeEngine: ThemeEngine,
  runtimePermissionsHelper: RuntimePermissionsHelper?,
  content: @Composable () -> Unit
) {
  ProvideComponentActivity(componentActivity) {
    ProvideKurobaViewConfiguration {
      ProvideWindowInsets(window = window) {
        ProvideChanTheme(themeEngine = themeEngine) {
          ProvideLocalRuntimePermissionsHelper(runtimePermissionsHelper = runtimePermissionsHelper) {
            content()
          }
        }
      }
    }
  }
}

@Composable
fun ProvideChanTheme(
  themeEngine: ThemeEngine,
  content: @Composable () -> Unit
) {
  var chanTheme by remember { mutableStateOf(themeEngine.chanTheme) }

  DisposableEffect(themeEngine.chanTheme) {
    val themeUpdateObserver = object : ThemeEngine.ThemeChangesListener {
      override fun onThemeChanged() {
        chanTheme = themeEngine.chanTheme.fullCopy()
      }
    }

    themeEngine.addListener(themeUpdateObserver)
    onDispose { themeEngine.removeListener(themeUpdateObserver) }
  }

  CompositionLocalProvider(LocalChanTheme provides chanTheme) {
    content()
  }
}

@Composable
fun ProvideComponentActivity(
  componentActivity: ComponentActivity,
  content: @Composable () -> Unit
) {
  CompositionLocalProvider(LocalComponentActivity provides componentActivity) {
    content()
  }
}

@Composable
fun ProvideKurobaViewConfiguration(
  content: @Composable () -> Unit
) {
  val context = LocalContext.current

  val kurobaViewConfiguration = remember {
    KurobaViewConfiguration(android.view.ViewConfiguration.get(context))
  }

  CompositionLocalProvider(LocalViewConfiguration provides kurobaViewConfiguration) {
    content()
  }
}

@Composable
fun ProvideLocalRuntimePermissionsHelper(
  runtimePermissionsHelper: RuntimePermissionsHelper?,
  content: @Composable () -> Unit
) {
  if (runtimePermissionsHelper == null) {
    content()
    return
  }

  CompositionLocalProvider(LocalRuntimePermissionsHelper provides runtimePermissionsHelper) {
    content()
  }
}

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

class KurobaViewConfiguration(
  private val viewConfiguration: android.view.ViewConfiguration
) : ViewConfiguration {
  override val longPressTimeoutMillis: Long
    get() = android.view.ViewConfiguration.getLongPressTimeout().toLong()
  override val doubleTapTimeoutMillis: Long
    get() = android.view.ViewConfiguration.getDoubleTapTimeout().toLong()
  override val doubleTapMinTimeMillis: Long
    get() = 40
  override val touchSlop: Float
    get() = viewConfiguration.scaledTouchSlop.toFloat()
  override val minimumTouchTargetSize: DpSize
    get() = DpSize(20.dp, 20.dp)
}

@Immutable
data class Insets(
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

  fun copyInsets(
    newLeft: Dp = left,
    newRight: Dp = right,
    newTop: Dp = top,
    newBottom: Dp = bottom,
  ): Insets {
    return Insets(density, newLeft, newRight, newTop, newBottom)
  }

}