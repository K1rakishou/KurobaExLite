package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenTransition
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen

@Suppress("UnnecessaryVariable", "FoldInitializerAndIfToElvis")
@Composable
fun RootRouterHost(
  rootNavigationRouter: NavigationRouter
) {
  val screenUpdateTransactionState by rootNavigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdateTransaction = screenUpdateTransactionState
  val saveableStateHolder = rememberSaveableStateHolder()

  if (screenUpdateTransaction == null) {
    return
  }

  if (screenUpdateTransaction.navigationScreenUpdates.isNotEmpty()) {
    for (primaryScreenUpdate in screenUpdateTransaction.navigationScreenUpdates) {
      saveableStateHolder.SaveableStateProvider(key = "primary_${primaryScreenUpdate.screen.screenKey.key}") {
        key(primaryScreenUpdate.screen.screenKey) {
          ScreenTransition(
            screenUpdate = primaryScreenUpdate,
            onScreenUpdateFinished = { screenUpdate ->
              rootNavigationRouter.onScreenUpdateFinished(screenUpdate)
            },
            content = { primaryScreenUpdate.screen.Content() }
          )
        }
      }
    }
  }

  if (screenUpdateTransaction.floatingScreenUpdates.isNotEmpty()) {
    for (floatingScreenUpdate in screenUpdateTransaction.floatingScreenUpdates) {
      saveableStateHolder.SaveableStateProvider(key = "floating_${floatingScreenUpdate.screen.screenKey.key}") {
        key(floatingScreenUpdate.screen.screenKey) {
          ScreenTransition(
            screenUpdate = floatingScreenUpdate,
            onScreenUpdateFinished = { screenUpdate ->
              rootNavigationRouter.onScreenUpdateFinished(screenUpdate)
            },
            content = { floatingScreenUpdate.screen.Content() }
          )
        }
      }
    }
  }
}

@Composable
fun RouterHost(
  navigationRouter: NavigationRouter,
  defaultScreenFunc: @Composable () -> ComposeScreen
) {
  val saveableStateHolder = rememberSaveableStateHolder()
  val defaultScreen = defaultScreenFunc()

  saveableStateHolder.SaveableStateProvider(key = "primary_${defaultScreen.screenKey.key}") {
    // Hack for default screen lifecycle since we never add it into the navigation router.
    DisposableEffect(
      key1 = Unit,
      effect = {
        defaultScreen.onStartCreating()
        defaultScreen.onCreated()

        onDispose {
          defaultScreen.onStartDisposing()
          defaultScreen.onDisposed()
        }
      }
    )

    defaultScreen.Content()
  }

  val screenUpdateTransactionState by navigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdateTransaction = screenUpdateTransactionState

  if (screenUpdateTransaction == null) {
    return
  }

  if (screenUpdateTransaction.navigationScreenUpdates.isNotEmpty()) {
    for (primaryScreenUpdate in screenUpdateTransaction.navigationScreenUpdates) {
      saveableStateHolder.SaveableStateProvider(key = "primary_${primaryScreenUpdate.screen.screenKey.key}") {
        key(primaryScreenUpdate.screen.screenKey) {
          ScreenTransition(
            screenUpdate = primaryScreenUpdate,
            onScreenUpdateFinished = { screenUpdate ->
              navigationRouter.onScreenUpdateFinished(screenUpdate)
            },
            content = { primaryScreenUpdate.screen.Content() }
          )
        }
      }
    }
  }
}