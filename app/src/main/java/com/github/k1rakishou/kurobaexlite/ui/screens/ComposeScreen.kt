package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

abstract class ComposeScreen(
  protected val componentActivity: ComponentActivity
) {

  abstract val screenKey: ScreenKey

  @Composable
  abstract fun Content()

  @Composable
  protected fun pushScreenOnce(
    navigationRouter: NavigationRouter,
    composeScreenBuilder: () -> ComposeScreen
  ) {
    LaunchedEffect(key1 = Unit, block = { navigationRouter.pushScreen(composeScreenBuilder()) })
  }

}

inline class ScreenKey(val key: String)