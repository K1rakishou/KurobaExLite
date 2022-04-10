package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

abstract class ComposeScreenWithToolbar(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {

  @Composable
  abstract fun Toolbar(boxScope: BoxScope)

  @Composable
  fun topChildScreen(): ComposeScreenWithToolbar {
    val navigationScreensStack = navigationRouter.navigationScreensStack
    if (navigationScreensStack.isEmpty()) {
      return this
    }

    return navigationScreensStack.last() as ComposeScreenWithToolbar
  }

}