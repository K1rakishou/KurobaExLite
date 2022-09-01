package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenTransition
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen

@Suppress("UnnecessaryVariable", "FoldInitializerAndIfToElvis")
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

  for (primaryScreen in navigationRouter.navigationScreensStack) {
    val key = "primary_${primaryScreen.screenKey.key}"

    val contentLambda = cache.getOrPut(
      key = key,
      defaultValue = {
        movableContentOf {
          key(primaryScreen.screenKey) {
            primaryScreen.Content()
          }
        }
      }
    )

    var screenAnimation = navigationRouter.screenAnimations[primaryScreen.screenKey]
    if (screenAnimation == null) {
      if (!navigationRouter.screenExistsInThisRouter(primaryScreen.screenKey)) {
        continue
      }

      screenAnimation = NavigationRouter.ScreenAnimation.Set(primaryScreen.screenKey)
    }

    ScreenTransition(
      composeScreen = primaryScreen,
      screenAnimation = screenAnimation,
      onScreenAnimationFinished = { finishedScreenAnimation ->
        navigationRouter.onScreenAnimationFinished(finishedScreenAnimation)

        if (finishedScreenAnimation.isScreenBeingRemoved()) {
          val key = "primary_${finishedScreenAnimation.screenKey.key}"

          cache.remove(key)
          saveableStateHolder.removeState(key)
        }
      },
      content = {
        saveableStateHolder.SaveableStateProvider(key = key) {
          contentLambda()
        }
      }
    )
  }

  if (navigationRouter is MainNavigationRouter) {
    for (floatingScreen in navigationRouter.floatingScreensStack) {
      val key = "floating_${floatingScreen.screenKey.key}"

      val contentLambda = cache.getOrPut(
        key = key,
        defaultValue = {
          movableContentOf {
            key(floatingScreen.screenKey) {
              floatingScreen.Content()
            }
          }
        }
      )

      var screenAnimation = navigationRouter.screenAnimations[floatingScreen.screenKey]
      if (screenAnimation == null) {
        if (!navigationRouter.screenExistsInThisRouter(floatingScreen.screenKey)) {
          continue
        }

        screenAnimation = NavigationRouter.ScreenAnimation.Set(floatingScreen.screenKey)
      }

      ScreenTransition(
        composeScreen = floatingScreen,
        screenAnimation = screenAnimation,
        onScreenAnimationFinished = { finishedScreenAnimation ->
          navigationRouter.onScreenAnimationFinished(finishedScreenAnimation)

          if (finishedScreenAnimation.isScreenBeingRemoved()) {
            val key = "floating_${finishedScreenAnimation.screenKey.key}"

            cache.remove(key)
            saveableStateHolder.removeState(key)
          }
        },
        content = {
          saveableStateHolder.SaveableStateProvider(key = key) {
            contentLambda()
          }
        }
      )
    }
  }
}