package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.*
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenTransition

@Composable
fun RootRouterHost(
  rootNavigationRouter: NavigationRouter,
  onBackPressed: () -> Boolean = { false }
) {
  DisposableEffect(key1 = Unit) {
    val handler = object : NavigationRouter.OnBackPressHandler {
      override suspend fun onBackPressed(): Boolean {
        return onBackPressed()
      }
    }

    rootNavigationRouter.addOnBackPressedHandler(handler)

    onDispose {
      rootNavigationRouter.removeOnBackPressedHandler(handler)
    }
  }

  val screenUpdateTransactionState by rootNavigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdateTransaction = screenUpdateTransactionState ?: return

  if (screenUpdateTransaction.navigationScreenUpdates.isNotEmpty()) {
    for (secondaryScreenUpdate in screenUpdateTransaction.navigationScreenUpdates) {
      key(secondaryScreenUpdate.screen.screenKey) {
        ScreenTransition(
          screenUpdate = secondaryScreenUpdate,
          content = { secondaryScreenUpdate.screen.Content() }
        )
      }
    }
  }

  if (screenUpdateTransaction.floatingScreenUpdates.isNotEmpty()) {
    for (secondaryScreenUpdate in screenUpdateTransaction.floatingScreenUpdates) {
      key(secondaryScreenUpdate.screen.screenKey) {
        ScreenTransition(
          screenUpdate = secondaryScreenUpdate,
          content = { secondaryScreenUpdate.screen.Content() }
        )
      }
    }
  }
}

@Composable
fun RouterHost(
  navigationRouter: NavigationRouter,
  defaultScreen: @Composable () -> Unit,
  onBackPressed: () -> Boolean = { false }
) {
  DisposableEffect(key1 = Unit) {
    val handler = object : NavigationRouter.OnBackPressHandler {
      override suspend fun onBackPressed(): Boolean {
        return onBackPressed()
      }
    }

    navigationRouter.addOnBackPressedHandler(handler)

    onDispose {
      navigationRouter.removeOnBackPressedHandler(handler)
    }
  }

  val screenUpdateTransactionState by navigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdateTransaction = screenUpdateTransactionState

  if (screenUpdateTransaction == null) {
    defaultScreen()
    return
  }

  if (screenUpdateTransaction.navigationScreenUpdates.isNotEmpty()) {
    for (secondaryScreenUpdate in screenUpdateTransaction.navigationScreenUpdates) {
      key(secondaryScreenUpdate.screen.screenKey) {
        ScreenTransition(
          screenUpdate = secondaryScreenUpdate,
          content = { secondaryScreenUpdate.screen.Content() }
        )
      }
    }
  }

  if (screenUpdateTransaction.floatingScreenUpdates.isNotEmpty()) {
    for (secondaryScreenUpdate in screenUpdateTransaction.floatingScreenUpdates) {
      key(secondaryScreenUpdate.screen.screenKey) {
        ScreenTransition(
          screenUpdate = secondaryScreenUpdate,
          content = { secondaryScreenUpdate.screen.Content() }
        )
      }
    }
  }
}