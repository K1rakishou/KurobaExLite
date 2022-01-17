package com.github.k1rakishou.kurobaexlite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Navigation {
  private val navigationStack = mutableListOf<NavigationScreen>()
  private val dialogsStack = mutableListOf<NavigationScreen>()

  private val _screenUpdatesFlow = MutableStateFlow<ScreenUpdate?>(null)
  val screenUpdatesFlow: StateFlow<ScreenUpdate?>
    get() = _screenUpdatesFlow.asStateFlow()

  fun pushScreen(navigationScreen: NavigationScreen) {
    navigationStack.add(navigationScreen)
    _screenUpdatesFlow.value = ScreenUpdate.Push(navigationScreen)
  }

  fun pushOrReplaceTopScreen(navigationScreen: NavigationScreen) {

  }

  fun popTopScreen() {
    val removedScreen = navigationStack.removeLastOrNull()
      ?: return

    _screenUpdatesFlow.value = ScreenUpdate.PopTop(removedScreen)
  }

  fun popUntil(predicate: (NavigationScreen) -> Boolean) {

  }

  fun pushDialogScreen(navigationScreen: NavigationScreen) {

  }

  fun popDialogScreen() {

  }

  fun popAllDialogScreens() {

  }

  fun topScreen(): NavigationScreen? {
    return navigationStack.lastOrNull()
  }

  fun screenByKey(screenKey: ScreenKey): NavigationScreen {
    return navigationStack.last { navigationScreen -> navigationScreen.key == screenKey }
  }

  fun hasScreens(): Boolean {
    return navigationStack.isNotEmpty()
  }

  sealed class ScreenUpdate(val screen: NavigationScreen) {

    fun isPop(): Boolean {
      return when (this) {
        is PopTop -> true
        else -> false
      }
    }

    class Push(screen: NavigationScreen) : ScreenUpdate(screen)
    class PopTop(screen: NavigationScreen) : ScreenUpdate(screen)
  }

}