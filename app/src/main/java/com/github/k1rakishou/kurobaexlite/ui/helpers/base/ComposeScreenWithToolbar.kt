package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.ScreenLayout

abstract class ComposeScreenWithToolbar(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {

  abstract val kurobaToolbarContainerState: KurobaToolbarContainerState<*>

  override suspend fun onDispose() {
    super.onDispose()

    kurobaToolbarContainerState.popChildToolbars()
  }

  @Composable
  abstract fun Toolbar(boxScope: BoxScope)

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

  fun childScreensCount(): Int {
    return navigationRouter.navigationScreensStack.size
  }

  fun hasChildScreens(): Boolean = navigationRouter.navigationScreensStack.isNotEmpty()

}