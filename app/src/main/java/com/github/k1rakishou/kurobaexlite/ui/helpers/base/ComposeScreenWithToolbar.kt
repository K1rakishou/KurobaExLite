package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

abstract class ComposeScreenWithToolbar<ToolbarType : KurobaChildToolbar>(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  protected val kurobaToolbarContainerViewModel: KurobaToolbarContainerViewModel by componentActivity.viewModel()

  abstract val hasFab: Boolean
  abstract val defaultToolbar: ToolbarType

  protected abstract val kurobaToolbarContainerState: KurobaToolbarContainerState<ToolbarType>

  override fun onStartCreating() {
    super.onStartCreating()

    kurobaToolbarContainerState.setDefaultToolbar(defaultToolbar)
  }

  override fun onStartDisposing() {
    super.onStartDisposing()

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

    val resultList = mutableListWithCap<ComposeScreenWithToolbar<ToolbarType>>(2)
    resultList.add(this)
    resultList.add(navigationScreensStack.last() as ComposeScreenWithToolbar<ToolbarType>)

    return resultList
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