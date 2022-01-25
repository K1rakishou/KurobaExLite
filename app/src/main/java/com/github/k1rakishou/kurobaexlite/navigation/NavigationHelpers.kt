package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenTransition

@Composable
fun RootRouterHost(
  rootNavigationRouter: NavigationRouter,
  onBackPressed: () -> Boolean = { false },
  onTransitionFinished: (NavigationRouter.ScreenUpdate) -> Unit = { }
) {
  DisposableEffect(key1 = Unit) {
    val handler = object : NavigationRouter.OnBackPressHandler {
      override fun onBackPressed(): Boolean {
        return onBackPressed()
      }
    }

    rootNavigationRouter.addOnBackPressedHandler(handler)

    onDispose {
      rootNavigationRouter.removeOnBackPressedHandler(handler)
    }
  }

  val screenUpdateState by rootNavigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdate = screenUpdateState
    ?: return

  ScreenTransition(
    screenUpdate = screenUpdate,
    onTransitionFinished = onTransitionFinished
  ) {
    screenUpdate.screen.Content()
  }
}

@Composable
fun RouterHost(
  navigationRouter: NavigationRouter,
  defaultScreen: @Composable () -> Unit,
  onBackPressed: () -> Boolean = { false },
  onTransitionFinished: (NavigationRouter.ScreenUpdate) -> Unit = { }
) {
  DisposableEffect(key1 = Unit) {
    val handler = object : NavigationRouter.OnBackPressHandler {
      override fun onBackPressed(): Boolean {
        return onBackPressed()
      }
    }

    navigationRouter.addOnBackPressedHandler(handler)

    onDispose {
      navigationRouter.removeOnBackPressedHandler(handler)
    }
  }

  val screenUpdateState by navigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdate = screenUpdateState

  if (screenUpdate == null) {
    defaultScreen()
    return
  }

  ScreenTransition(
    screenUpdate = screenUpdate,
    onTransitionFinished = onTransitionFinished
  ) {
    screenUpdate.screen.Content()
  }
}