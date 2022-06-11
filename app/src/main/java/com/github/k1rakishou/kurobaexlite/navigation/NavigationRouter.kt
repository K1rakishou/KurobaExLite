package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.github.k1rakishou.kurobaexlite.helpers.unreachable
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority
import logcat.logcat

open class NavigationRouter(
  val routerKey: ScreenKey,
  private val parentRouter: NavigationRouter?
) {
  protected val _navigationScreensStack = mutableStateListOf<ComposeScreen>()
  val navigationScreensStack: List<ComposeScreen>
    get() = _navigationScreensStack

  protected val childRouters = linkedMapOf<ScreenKey, NavigationRouter>()

  protected val _screenUpdatesFlow = MutableStateFlow<ScreenUpdateTransaction?>(null)
  val screenUpdatesFlow: StateFlow<ScreenUpdateTransaction?>
    get() = _screenUpdatesFlow.asStateFlow()

  protected val removingScreens = mutableSetOf<ScreenKey>()

  open fun pushScreen(composeScreen: ComposeScreen): Boolean {
    return pushScreen(composeScreen, true)
  }

  open fun pushScreen(composeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    if (composeScreen is FloatingComposeScreen) {
      error("FloatingComposeScreens must be added via presentScreen() function!")
    }

    val indexOfPrev = _navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == composeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return false
    }

    val newScreenUpdate = if (withAnimation) {
      ScreenUpdate.Push(composeScreen)
    } else {
      ScreenUpdate.Set(composeScreen)
    }

    val navigationScreenUpdates = combineScreenUpdates(
      oldScreens = _navigationScreensStack,
      newScreenUpdate = newScreenUpdate
    )

    _navigationScreensStack.add(composeScreen)
    logcat(TAG, LogPriority.VERBOSE) { "pushScreen(${composeScreen.screenKey.key})" }
    composeScreen.onStartCreating()

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = emptyList()
    )

    return true
  }

  open fun popScreen(composeScreen: ComposeScreen): Boolean {
    return popScreen(composeScreen, true)
  }

  open fun popScreen(composeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    val indexOfPrev = _navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == composeScreen.screenKey }

    if (indexOfPrev < 0) {
      // Already removed
      return false
    }

    if (!removingScreens.add(composeScreen.screenKey)) {
      return false
    }

    val newScreenUpdate = if (withAnimation) {
      ScreenUpdate.Pop(composeScreen)
    } else {
      ScreenUpdate.Remove(composeScreen)
    }

    val oldScreens = _navigationScreensStack
      .filter { screen -> screen.screenKey != composeScreen.screenKey }

    val navigationScreenUpdates = combineScreenUpdates(
      oldScreens = oldScreens,
      newScreenUpdate = newScreenUpdate
    )

    logcat(TAG, LogPriority.VERBOSE) { "popScreen(${composeScreen.screenKey.key})" }
    composeScreen.onStartDisposing()

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = emptyList()
    )

    return true
  }

  open fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
    if (parentRouter != null) {
      parentRouter.presentScreen(floatingComposeScreen)
      return
    }

    unreachable("Must never reach here because should be overridden by MainNavigationRouter")
  }

  open fun stopPresentingScreen(
    screenKey: ScreenKey,
    overrideAnimation: ScreenRemoveAnimation? = null
  ): Boolean {
    if (parentRouter != null) {
      return parentRouter.stopPresentingScreen(screenKey, overrideAnimation)
    }

    unreachable("Must never reach here because should be overridden by MainNavigationRouter")
  }

  protected fun combineScreenUpdates(
    oldScreens: List<ComposeScreen>,
    newScreenUpdate: ScreenUpdate
  ): List<ScreenUpdate> {
    return oldScreens.map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) } + newScreenUpdate
  }

  fun childRouter(screenKey: ScreenKey): NavigationRouter {
    return childRouters.getOrPut(
      key = screenKey,
      defaultValue = {
        NavigationRouter(
          routerKey = screenKey,
          parentRouter = this
        )
      }
    )
  }

  fun getParentRouterByKey(screenKey: ScreenKey): NavigationRouter {
    if (routerKey == screenKey) {
      return this
    }

    if (parentRouter != null) {
      val router = parentRouter.getParentRouterByKeyOrNull(screenKey)
      if (router != null) {
        return router
      }
    }

    error("getParentRouterByKey() NavigationRouter with key \'${screenKey.key}\' not found")
  }

  fun getParentRouterByKeyOrNull(screenKey: ScreenKey): NavigationRouter? {
    if (routerKey == screenKey) {
      return this
    }

    if (parentRouter != null) {
      return parentRouter.getParentRouterByKeyOrNull(screenKey)
    }

    return null
  }

  fun getChildRouterByKey(screenKey: ScreenKey): NavigationRouter {
    if (routerKey == screenKey) {
      return this
    }

    for (childRouter in childRouters.values) {
      val router = childRouter.getChildRouterByKeyOrNull(screenKey)
      if (router != null) {
        return router
      }
    }

    error("getChildRouterByKey() NavigationRouter with key \'${screenKey.key}\' not found")
  }

  fun getChildRouterByKeyOrNull(screenKey: ScreenKey): NavigationRouter? {
    if (routerKey == screenKey) {
      return this
    }

    for (childRouter in childRouters.values) {
      val router = childRouter.getParentRouterByKeyOrNull(screenKey)
      if (router != null) {
        return router
      }
    }

    return null
  }

  fun getScreenByKey(screenKey: ScreenKey): ComposeScreen? {
    val rootRouter = getRootRouter()
    return getScreenByKeyInternal(rootRouter, screenKey)
  }

  private fun getScreenByKeyInternal(
    currentRouter: NavigationRouter,
    screenKey: ScreenKey
  ): ComposeScreen? {
    if (currentRouter is MainNavigationRouter) {
      val screen = currentRouter.floatingScreensStack
        .firstOrNull { floatingScreen -> floatingScreen.screenKey == screenKey }

      if (screen != null) {
        return screen
      }
    }

    val screen = currentRouter.navigationScreensStack
      .firstOrNull { floatingScreen -> floatingScreen.screenKey == screenKey }

    if (screen != null) {
      return screen
    }

    for ((_, childRouter) in childRouters) {
      val screen = getScreenByKeyInternal(childRouter, screenKey)
      if (screen != null) {
        return screen
      }
    }

    return null
  }

  private fun getRootRouter(): NavigationRouter {
    if (parentRouter == null) {
      return this
    }

    return parentRouter.getRootRouter()
  }

  open suspend fun onScreenUpdateFinished(screenUpdate: ScreenUpdate) {
    if (!screenUpdate.isScreenBeingRemoved()) {
      screenUpdate.screen.onCreated()
      return
    }

    screenUpdate.screen.onDisposed()

    _navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == screenUpdate.screen.screenKey }
      .takeIf { index -> index >= 0 }
      ?.let { indexOfRemovedScreen ->
        removingScreens.remove(screenUpdate.screen.screenKey)
        _navigationScreensStack.removeAt(indexOfRemovedScreen)
      }

    if (_navigationScreensStack.isEmpty()) {
      _screenUpdatesFlow.value = null
      return
    }

    removeUpdateAfterAnimationFinished(screenUpdate)
  }

  protected fun removeUpdateAfterAnimationFinished(screenUpdate: ScreenUpdate) {
    val prevScreenUpdates = _screenUpdatesFlow.value
      ?: return

    val indexOfOldFloatingScreenUpdate = prevScreenUpdates.floatingScreenUpdates
      .indexOfFirst { oldScreenUpdate -> oldScreenUpdate === screenUpdate }
    val indexOfOldNavigationScreenUpdate = prevScreenUpdates.navigationScreenUpdates
      .indexOfFirst { oldScreenUpdate -> oldScreenUpdate === screenUpdate }

    var needUpdate = false

    val newFloatingScreenUpdates = if (indexOfOldFloatingScreenUpdate >= 0) {
      needUpdate = true

      val prevList = prevScreenUpdates.floatingScreenUpdates.toMutableList()
      prevList.removeAt(indexOfOldFloatingScreenUpdate)
      prevList
    } else {
      prevScreenUpdates.floatingScreenUpdates
    }

    val newNavigationScreenUpdates = if (indexOfOldNavigationScreenUpdate >= 0) {
      needUpdate = true

      val prevList = prevScreenUpdates.navigationScreenUpdates.toMutableList()
      prevList.removeAt(indexOfOldNavigationScreenUpdate)
      prevList
    } else {
      prevScreenUpdates.navigationScreenUpdates
    }

    if (!needUpdate) {
      return
    }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = newNavigationScreenUpdates,
      floatingScreenUpdates = newFloatingScreenUpdates
    )
  }

  data class ScreenUpdateTransaction(
    val navigationScreenUpdates: List<ScreenUpdate>,
    val floatingScreenUpdates: List<ScreenUpdate>
  )

  @Stable
  sealed class ScreenUpdate(val screen: ComposeScreen) {

    fun isScreenBeingRemoved(): Boolean {
      return when (this) {
        is Fade -> this.fadeType == FadeType.Out
        is Pop -> true
        is Push -> false
        is Set -> false
        is Remove -> true
      }
    }
    
    data class Set(val composeScreen: ComposeScreen) : ScreenUpdate(composeScreen) {
      override fun toString(): String = "Set(key=${composeScreen.screenKey.key})"
    }
    data class Remove(val composeScreen: ComposeScreen) : ScreenUpdate(composeScreen) {
      override fun toString(): String = "Remove(key=${composeScreen.screenKey.key})"
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

  enum class ScreenAddAnimation {
    Push,
    FadeIn
  }

  enum class ScreenRemoveAnimation {
    Pop,
    FadeOut
  }

  companion object {
    private const val TAG = "NavigationRouter"
  }

}