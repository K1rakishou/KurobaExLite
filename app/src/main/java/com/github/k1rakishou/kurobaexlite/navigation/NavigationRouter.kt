package com.github.k1rakishou.kurobaexlite.navigation

import android.content.Intent
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class NavigationRouter(
  private val routerIndex: Int? = null,
  private val parentRouter: NavigationRouter?
) {
  private val navigationScreensStack = mutableListOf<ComposeScreen>()
  private val floatingScreensStack = mutableListOf<FloatingComposeScreen>()
  private val childRouters = mutableMapOf<String, NavigationRouter>()
  private val backPressHandlers = mutableListOf<OnBackPressHandler>()

  private val _screenUpdatesFlow = MutableStateFlow<ScreenUpdateTransaction?>(null)
  val screenUpdatesFlow: StateFlow<ScreenUpdateTransaction?>
    get() = _screenUpdatesFlow.asStateFlow()

  private val _intentsFlow = MutableSharedFlow<Intent>(extraBufferCapacity = Channel.UNLIMITED)
  val intentsFlow: SharedFlow<Intent>
    get() = _intentsFlow.asSharedFlow()

  fun pushScreen(newComposeScreen: ComposeScreen) {
    val indexOfPrev = navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == newComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return
    }

    val navigationScreenUpdates = combineScreenUpdates(
      oldScreens = navigationScreensStack,
      newScreenUpdate = ScreenUpdate.Push(newComposeScreen)
    )
    val floatingScreenUpdates = floatingScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }

    navigationScreensStack.add(newComposeScreen)
    logcat.logcat(tag = "ScreenTransition") { "pushScreen(${newComposeScreen.screenKey.key})" }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )
  }

  fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
    if (parentRouter != null) {
      parentRouter.presentScreen(floatingComposeScreen)
      return
    }

    val indexOfPrev = floatingScreensStack
      .indexOfFirst { screen -> screen.screenKey == floatingComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return
    }

    val navigationScreenUpdates = navigationScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }
    val floatingScreenUpdates = combineScreenUpdates(
      oldScreens = floatingScreensStack,
      newScreenUpdate = ScreenUpdate.Fade(floatingComposeScreen, ScreenUpdate.FadeType.In)
    )

    floatingScreensStack.add(floatingComposeScreen)
    logcat.logcat(tag = "ScreenTransition") { "presentScreen(${floatingComposeScreen.screenKey.key})" }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )
  }

  fun stopPresentingScreen(screenKey: ScreenKey): Boolean {
    if (parentRouter != null) {
      return parentRouter.stopPresentingScreen(screenKey)
    }

    val index = floatingScreensStack.indexOfLast { screen -> screen.screenKey == screenKey }
    if (index < 0) {
      return false
    }

    val floatingComposeScreen = floatingScreensStack.removeAt(index)

    val navigationScreenUpdates = navigationScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }
    val floatingScreenUpdates = combineScreenUpdates(
      oldScreens = floatingScreensStack,
      newScreenUpdate = ScreenUpdate.Fade(floatingComposeScreen, ScreenUpdate.FadeType.Out)
    )

    logcat.logcat(tag = "ScreenTransition") { "stopPresenting(${floatingComposeScreen.screenKey.key})" }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )

    return true
  }

  private fun combineScreenUpdates(
    oldScreens: List<ComposeScreen>,
    newScreenUpdate: ScreenUpdate
  ): List<ScreenUpdate> {
    return oldScreens.map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) } + newScreenUpdate
  }

  fun topNavigationScreen(): ComposeScreen? {
    return navigationScreensStack.lastOrNull()
  }

  fun topFloatingScreen(): ComposeScreen? {
    return floatingScreensStack.lastOrNull()
  }

  fun screenByKey(screenKey: ScreenKey): ComposeScreen? {
    val screen = navigationScreensStack
      .lastOrNull { navigationScreen -> navigationScreen.screenKey == screenKey }

    if (screen != null) {
      return screen
    }

    return floatingScreensStack
      .lastOrNull { navigationScreen -> navigationScreen.screenKey == screenKey }
  }

  fun hasScreens(): Boolean {
    return navigationScreensStack.isNotEmpty() || floatingScreensStack.isNotEmpty()
  }

  fun childRouter(key: String, routerIndex: Int? = null): NavigationRouter {
    return childRouters.getOrPut(
      key = key,
      defaultValue = { NavigationRouter(routerIndex = routerIndex, parentRouter = this) }
    )
  }

  fun onNewIntent(intent: Intent) {
    _intentsFlow.tryEmit(intent)

    for (childRouter in childRouters.values) {
      childRouter.onNewIntent(intent)
    }
  }

  fun addOnBackPressedHandler(handler: OnBackPressHandler) {
    backPressHandlers += handler
  }

  fun removeOnBackPressedHandler(handler: OnBackPressHandler) {
    backPressHandlers -= handler
  }

  fun onBackPressed(): Boolean {
    if (backPressHandlers.isEmpty()) {
      return false
    }

    if (childRouters.isNotEmpty()) {
      val routersSorted = childRouters.entries
        .sortedBy { (_, navigationRouter) -> navigationRouter.routerIndex ?: 0 }

      for ((_, navigationRouter) in routersSorted) {
        if (navigationRouter.onBackPressed()) {
          return true
        }
      }
    }

    for (backPressHandler in backPressHandlers) {
      if (backPressHandler.onBackPressed()) {
        return true
      }
    }

    return false
  }

  data class ScreenUpdateTransaction(
    val navigationScreenUpdates: List<ScreenUpdate>,
    val floatingScreenUpdates: List<ScreenUpdate>
  )

  sealed class ScreenUpdate(val screen: ComposeScreen) {
    
    data class Set(val composeScreen: ComposeScreen) : ScreenUpdate(composeScreen) {
      override fun toString(): String = "Set(key=${composeScreen.screenKey.key})"
    }
    data class Push(val composeScreen: ComposeScreen) : ScreenUpdate(composeScreen) {
      override fun toString(): String = "Push(key=${composeScreen.screenKey.key})"
    }
    data class Pop(val composeScreen: ComposeScreen) : ScreenUpdate(composeScreen) {
      override fun toString(): String = "Pop(key=${composeScreen.screenKey.key})"
    }
    data class Fade(val composeScreen: ComposeScreen, val fadeType: FadeType) : ScreenUpdate(composeScreen) {
      override fun toString(): String = "Fade(key=${composeScreen.screenKey.key}, fadeType=$fadeType)"
    }

    enum class FadeType {
      In,
      Out
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as ScreenUpdate

      if (screen != other.screen) return false

      return true
    }

    override fun hashCode(): Int {
      return screen.hashCode()
    }

  }

  interface OnBackPressHandler {
    fun onBackPressed(): Boolean
  }

}