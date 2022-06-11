package com.github.k1rakishou.kurobaexlite.ui.helpers.floating

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

abstract class MinimizableFloatingComposeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  protected val isScreenMinimized = mutableStateOf(false)

  fun maximize() {
    isScreenMinimized.value = false
  }

  fun minimize() {
    isScreenMinimized.value = true
  }

}