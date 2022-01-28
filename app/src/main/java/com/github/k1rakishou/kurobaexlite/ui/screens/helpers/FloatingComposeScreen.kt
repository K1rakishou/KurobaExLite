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
import androidx.compose.runtime.remember
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

  open val contentAlignment: Alignment = Alignment.Center

  @CallSuper
  @Composable
  final override fun Content() {
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

    val insets = LocalWindowInsets.current
    val backgroundColor = remember { Color(red = 0f, green = 0f, blue = 0f, alpha = 0.6f) }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(backgroundColor)
        .kurobaClickable(hasClickIndication = false, onClick = { pop() })
    ) {
      val horizPadding = remember {
        if (globalConstants.isTablet) {
          HPADDING_TABLET_COMPOSE
        } else {
          HPADDING_COMPOSE
        }
      }

      val vertPadding = remember {
        if (globalConstants.isTablet) {
          VPADDING_TABLET_COMPOSE
        } else {
          VPADDING_COMPOSE
        }
      }

      Box(
        modifier = Modifier
          .padding(
            start = insets.leftDp + horizPadding,
            end = insets.rightDp + horizPadding,
            top = insets.topDp + vertPadding,
            bottom = insets.bottomDp + vertPadding,
          ),
        contentAlignment = contentAlignment,
      ) {
        FloatingContent()
      }
    }
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