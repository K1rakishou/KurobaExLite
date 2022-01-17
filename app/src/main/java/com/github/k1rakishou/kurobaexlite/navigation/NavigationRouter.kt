package com.github.k1rakishou.kurobaexlite.navigation

import android.content.Intent
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class NavigationRouter(
  private val routerIndex: Int? = null,
  private val parentRouter: NavigationRouter?
) {
  private val navigationStack = mutableListOf<ComposeScreen>()
  private val childRouters = mutableMapOf<String, NavigationRouter>()

  private val _screenUpdatesFlow = MutableStateFlow<ScreenUpdate?>(null)
  val screenUpdatesFlow: StateFlow<ScreenUpdate?>
    get() = _screenUpdatesFlow.asStateFlow()

  private val _intentsFlow = MutableSharedFlow<Intent>(extraBufferCapacity = Channel.UNLIMITED)
  val intentsFlow: SharedFlow<Intent>
    get() = _intentsFlow.asSharedFlow()

  fun pushScreenOnce(navigationScreen: ComposeScreen) {
    val indexOfPrev = navigationStack
      .indexOfFirst { screen -> screen.screenKey == navigationScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return
    }

    navigationStack.add(navigationScreen)
    _screenUpdatesFlow.value = ScreenUpdate.Push(navigationScreen)
  }

  fun pushOrReplaceTopScreen(navigationScreen: ComposeScreen) {

  }

  fun popTopScreen(): Boolean {
    val removedScreen = navigationStack.removeLastOrNull()
      ?: return false

    _screenUpdatesFlow.value = ScreenUpdate.Pop(removedScreen)
    return true
  }

  fun popUntil(predicate: (ComposeScreen) -> Boolean) {

  }

  fun topScreen(): ComposeScreen? {
    return navigationStack.lastOrNull()
  }

  fun screenByKey(screenKey: ScreenKey): ComposeScreen {
    return navigationStack.last { navigationScreen -> navigationScreen.screenKey == screenKey }
  }

  fun hasScreens(): Boolean {
    return navigationStack.isNotEmpty()
  }

  fun childRouter(routerIndex: Int? = null): NavigationRouter {
    return childRouter(key = DEFAULT_ROUTER_KEY, routerIndex = routerIndex)
  }

  fun childRouter(key: String, routerIndex: Int? = null): NavigationRouter {
    return childRouters.getOrPut(
      key = key,
      defaultValue = { NavigationRouter(routerIndex = routerIndex, parentRouter = this) }
    )
  }

  fun onBackPressed(): Boolean {
    if (childRouters.isNotEmpty()) {
      val handled = childRouters.entries
        .sortedBy { (_, value) -> value.routerIndex ?: Int.MAX_VALUE }
        .any { (_, innerChildRouter) -> innerChildRouter.onBackPressed() }

      if (handled) {
        return true
      }
    }

    return popTopScreen()
  }

  fun onNewIntent(intent: Intent) {
    _intentsFlow.tryEmit(intent)

    for (childRouter in childRouters.values) {
      childRouter.onNewIntent(intent)
    }
  }

  sealed class ScreenUpdate(val screen: ComposeScreen) {

    fun isPop(): Boolean {
      return when (this) {
        is Pop -> true
        else -> false
      }
    }

    class Push(screen: ComposeScreen) : ScreenUpdate(screen)
    class Pop(screen: ComposeScreen) : ScreenUpdate(screen)
  }

  companion object {
    private const val DEFAULT_ROUTER_KEY = "default_router"
  }

}