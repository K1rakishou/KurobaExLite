package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.ScreenLayout

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

  fun isScreenAtTop(screenKey: ScreenKey): Boolean {
    val topScreen = navigationRouter.navigationScreensStack.lastOrNull()
      ?: return false

    if (topScreen is ScreenLayout<*>) {
      return topScreen.childScreens
        .any { childScreen -> childScreen.composeScreen.screenKey == screenKey }
    }

    return topScreen.screenKey == screenKey
  }

  fun isChildScreensCount(): Int {
    return navigationRouter.navigationScreensStack.size
  }

  fun hasChildScreens(): Boolean = navigationRouter.navigationScreensStack.isNotEmpty()

}