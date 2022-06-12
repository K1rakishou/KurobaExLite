package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenTransition
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen

@Suppress("UnnecessaryVariable", "FoldInitializerAndIfToElvis")
@Composable
fun RootRouterHost(
  rootNavigationRouter: NavigationRouter,
  defaultScreenFunc: () -> ComposeScreen
) {
  LaunchedEffect(
    key1 = Unit,
    block = { rootNavigationRouter.pushScreen(defaultScreenFunc()) }
  )

  val screenUpdateTransactionState by rootNavigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdateTransaction = screenUpdateTransactionState
  val saveableStateHolder = rememberSaveableStateHolder()
  val cache = remember { mutableStateMapOf<String, @Composable () -> Unit>() }

  if (screenUpdateTransaction == null) {
    return
  }

  if (screenUpdateTransaction.navigationScreenUpdates.isNotEmpty()) {
    for (primaryScreenUpdate in screenUpdateTransaction.navigationScreenUpdates) {
      val key = "primary_${primaryScreenUpdate.screen.screenKey.key}"

      val contentLambda = cache.getOrPut(
        key = key,
        defaultValue = {
          movableContentOf {
            saveableStateHolder.SaveableStateProvider(key = key) {
              key(primaryScreenUpdate.screen.screenKey) {
                primaryScreenUpdate.screen.Content()
              }
            }
          }
        }
      )

      ScreenTransition(
        screenUpdate = primaryScreenUpdate,
        onScreenUpdateFinished = { screenUpdate ->
          rootNavigationRouter.onScreenUpdateFinished(screenUpdate)

          if (screenUpdate.isScreenBeingRemoved()) {
            val key = "primary_${screenUpdate.screen.screenKey.key}"

            cache.remove(key)
            saveableStateHolder.removeState(key)
          }
        },
        content = { contentLambda() }
      )
    }
  }

  if (screenUpdateTransaction.floatingScreenUpdates.isNotEmpty()) {
    for (floatingScreenUpdate in screenUpdateTransaction.floatingScreenUpdates) {
      val key = "floating_${floatingScreenUpdate.screen.screenKey.key}"

      val contentLambda = cache.getOrPut(
        key = key,
        defaultValue = {
          movableContentOf {
            saveableStateHolder.SaveableStateProvider(key = key) {
              key(floatingScreenUpdate.screen.screenKey) {
                floatingScreenUpdate.screen.Content()
              }
            }
          }
        }
      )

      ScreenTransition(
        screenUpdate = floatingScreenUpdate,
        onScreenUpdateFinished = { screenUpdate ->
          rootNavigationRouter.onScreenUpdateFinished(screenUpdate)

          if (screenUpdate.isScreenBeingRemoved()) {
            val key = "floating_${screenUpdate.screen.screenKey.key}"

            cache.remove(key)
            saveableStateHolder.removeState(key)
          }
        },
        content = { contentLambda() }
      )
    }
  }
}

@Composable
fun RouterHost(
  navigationRouter: NavigationRouter,
  defaultScreenFunc: () -> ComposeScreen
) {
  LaunchedEffect(
    key1 = Unit,
    block = { navigationRouter.pushScreen(defaultScreenFunc()) }
  )

  val saveableStateHolder = rememberSaveableStateHolder()
  val cache = remember { mutableStateMapOf<String, @Composable () -> Unit>() }

  val screenUpdateTransactionState by navigationRouter.screenUpdatesFlow.collectAsState()
  val screenUpdateTransaction = screenUpdateTransactionState

  if (screenUpdateTransaction == null) {
    return
  }

  if (screenUpdateTransaction.navigationScreenUpdates.isNotEmpty()) {
    for (primaryScreenUpdate in screenUpdateTransaction.navigationScreenUpdates) {
      val key = "primary_${primaryScreenUpdate.screen.screenKey.key}"

      val contentLambda = cache.getOrPut(
        key = key,
        defaultValue = {
          movableContentOf {
            saveableStateHolder.SaveableStateProvider(key = key) {
              key(primaryScreenUpdate.screen.screenKey) {
                primaryScreenUpdate.screen.Content()
              }
            }
          }
        }
      )

      ScreenTransition(
        screenUpdate = primaryScreenUpdate,
        onScreenUpdateFinished = { screenUpdate ->
          navigationRouter.onScreenUpdateFinished(screenUpdate)

          if (screenUpdate.isScreenBeingRemoved()) {
            val key = "primary_${screenUpdate.screen.screenKey.key}"

            cache.remove(key)
            saveableStateHolder.removeState(key)
          }
        },
        content = { contentLambda() }
      )
    }
  }
}