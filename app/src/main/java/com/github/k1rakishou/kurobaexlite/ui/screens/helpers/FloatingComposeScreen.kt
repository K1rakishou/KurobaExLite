package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets

abstract class FloatingComposeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val backgroundColor = Color(
    red = 0f,
    green = 0f,
    blue = 0f,
    alpha = 0.6f
  )

  val horizPaddingDp by lazy {
    if (uiInfoManager.isTablet) {
      HPADDING_TABLET_COMPOSE
    } else {
      HPADDING_COMPOSE
    }
  }

  val vertPaddingDp by lazy {
    if (uiInfoManager.isTablet) {
      VPADDING_TABLET_COMPOSE
    } else {
      VPADDING_COMPOSE
    }
  }

  val horizPaddingPx by lazy { with(uiInfoManager.composeDensity) { horizPaddingDp.toPx() } }
  val vertPaddingPx by lazy { with(uiInfoManager.composeDensity) { vertPaddingDp.toPx() } }

  open val contentAlignment: Alignment = Alignment.Center

  @CallSuper
  @Composable
  final override fun Content() {
    val insets = LocalWindowInsets.current

    HandleBackPresses()

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(backgroundColor)
        .kurobaClickable(
          hasClickIndication = false,
          onClick = { pop() }
        )
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(
            start = insets.leftDp + horizPaddingDp,
            end = insets.rightDp + horizPaddingDp,
            top = insets.topDp + vertPaddingDp,
            bottom = insets.bottomDp + vertPaddingDp,
          ),
        contentAlignment = contentAlignment,
      ) {
        FloatingContent()
      }
    }
  }

  @Composable
  private fun HandleBackPresses() {
    DisposableEffect(
      key1 = Unit,
      effect = {
        val handler = object : NavigationRouter.OnBackPressHandler {
          override fun onBackPressed(): Boolean {
            return this@FloatingComposeScreen.onBackPressed()
          }
        }

        navigationRouter.addOnBackPressedHandler(handler)

        onDispose {
          navigationRouter.removeOnBackPressedHandler(handler)
        }
      }
    )
  }

  @Composable
  abstract fun BoxScope.FloatingContent()

  @CallSuper
  open fun onBackPressed(): Boolean {
    return pop()
  }

  protected fun pop(): Boolean {
    return navigationRouter.stopPresenting()
  }

  companion object {
    val HPADDING_COMPOSE = 12.dp
    val VPADDING_COMPOSE = 16.dp

    val HPADDING_TABLET_COMPOSE = 32.dp
    val VPADDING_TABLET_COMPOSE = 48.dp
  }

}