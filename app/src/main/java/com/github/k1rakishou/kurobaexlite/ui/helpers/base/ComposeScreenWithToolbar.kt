package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState

abstract class ComposeScreenWithToolbar(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {

  protected abstract val kurobaToolbarContainerState: KurobaToolbarContainerState<*>

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

    return topScreen.screenKey == screenKey
  }

  fun childScreensCount(): Int {
    return navigationRouter.navigationScreensStack.size
  }

  fun hasChildScreens(): Boolean = navigationRouter.navigationScreensStack.isNotEmpty()

}