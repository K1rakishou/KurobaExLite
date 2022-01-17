package com.github.k1rakishou.kurobaexlite.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.*
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenTransition

@Composable
fun RootRouterHost(
  componentActivity: ComponentActivity,
  rootNavigationRouter: NavigationRouter,
  onTransitionFinished: (NavigationRouter.ScreenUpdate) -> Unit = { }
) {
  LaunchedEffect(key1 = Unit, block = {
    componentActivity.onBackPressedDispatcher.addCallback(
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          rootNavigationRouter.onBackPressed()
        }
      }
    )
  })

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
  onTransitionFinished: (NavigationRouter.ScreenUpdate) -> Unit = { }
) {
  val screenUpdateState by navigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdate = screenUpdateState
    ?: return

  ScreenTransition(
    screenUpdate = screenUpdate,
    onTransitionFinished = onTransitionFinished
  ) {
    screenUpdate.screen.Content()
  }
}