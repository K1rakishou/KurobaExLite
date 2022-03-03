package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenTransition

@Suppress("UnnecessaryVariable", "FoldInitializerAndIfToElvis")
@Composable
fun RootRouterHost(
  rootNavigationRouter: NavigationRouter,
  onBackPressed: () -> Boolean = { false }
) {
  rootNavigationRouter.HandleBackPresses(onBackPressed = onBackPressed)

  val screenUpdateTransactionState by rootNavigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdateTransaction = screenUpdateTransactionState

  if (screenUpdateTransaction == null) {
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

@Composable
fun RouterHost(
  navigationRouter: NavigationRouter,
  defaultScreen: @Composable () -> Unit,
  onBackPressed: () -> Boolean = { false }
) {
  navigationRouter.HandleBackPresses(onBackPressed = onBackPressed)

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