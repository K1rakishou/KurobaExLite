package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

abstract class ComposeScreenWithToolbar<ToolbarType : KurobaChildToolbar> protected constructor(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(screenArgs, componentActivity, navigationRouter) {
  protected val kurobaToolbarContainerViewModel: KurobaToolbarContainerViewModel by componentActivity.viewModel()

  abstract val hasFab: Boolean
  abstract val defaultToolbar: ToolbarType

  protected abstract val kurobaToolbarContainerState: KurobaToolbarContainerState<ToolbarType>

  @CallSuper
  override fun onStartCreating(screenCreateEvent: ScreenCreateEvent) {
    super.onStartCreating(screenCreateEvent)

    kurobaToolbarContainerState.setDefaultToolbar(defaultToolbar)
  }

  @CallSuper
  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    super.onDisposed(screenDisposeEvent)

    kurobaToolbarContainerState.popChildToolbars()
  }

  @Composable
  abstract fun Toolbar(boxScope: BoxScope)

  fun topChildScreen(): ComposeScreenWithToolbar<ToolbarType> {
    val navigationScreensStack = navigationRouter.navigationScreensStack
    if (navigationScreensStack.isEmpty()) {
      return this
    }

    return navigationScreensStack.last() as ComposeScreenWithToolbar<ToolbarType>
  }

  fun lastTwoChildScreens(): List<ComposeScreenWithToolbar<ToolbarType>> {
    val navigationScreensStack = navigationRouter.navigationScreensStack
    if (navigationScreensStack.isEmpty()) {
      return listOf(this)
    }

    if (navigationScreensStack.size >= 2) {
      return navigationScreensStack.takeLast(2) as List<ComposeScreenWithToolbar<ToolbarType>>
    }

    return listOf(
      navigationScreensStack.last() as ComposeScreenWithToolbar<ToolbarType>
    )
  }

  fun isScreenAtTop(screenKey: ScreenKey): Boolean {
    val topScreen = navigationRouter.navigationScreensStack.lastOrNull()
      ?: return false

    return topScreen.screenKey == screenKey
  }

  fun hasChildScreens(): Boolean = navigationRouter.navigationScreensStack.size > 1

  fun canDragPager(): Boolean {
    if (navigationRouter.navigationScreensStack.isEmpty()) {
      return true
    }

    val topScreen = navigationRouter.navigationScreensStack.last()
    if (topScreen is HomeNavigationScreen<*>) {
      return !topScreen.dragToCloseEnabled
    }

    return true
  }

}