package com.github.k1rakishou.kurobaexlite.navigation

import android.content.Intent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.github.k1rakishou.kurobaexlite.helpers.unreachable
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority
import logcat.logcat

open class NavigationRouter(
  val routerKey: ScreenKey,
  private val parentRouter: NavigationRouter?
) {
  protected val _navigationScreensStack = mutableStateListOf<ComposeScreen>()
  val navigationScreensStack: List<ComposeScreen>
    get() = _navigationScreensStack.toList()

  protected val childRouters = linkedMapOf<ScreenKey, NavigationRouter>()

  protected val _screenUpdatesFlow = MutableStateFlow<ScreenUpdateTransaction?>(null)
  val screenUpdatesFlow: StateFlow<ScreenUpdateTransaction?>
    get() = _screenUpdatesFlow.asStateFlow()

  private val _intentsFlow = MutableSharedFlow<Intent>(extraBufferCapacity = Channel.UNLIMITED)
  val intentsFlow: SharedFlow<Intent>
    get() = _intentsFlow.asSharedFlow()

  open fun pushScreen(newComposeScreen: ComposeScreen): Boolean {
    if (newComposeScreen is FloatingComposeScreen) {
      error("FloatingComposeScreens must be added via presentScreen() function!")
    }

    val indexOfPrev = _navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == newComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return false
    }

    val navigationScreenUpdates = combineScreenUpdates(
      oldScreens = _navigationScreensStack,
      newScreenUpdate = ScreenUpdate.Push(newComposeScreen)
    )

    _navigationScreensStack.add(newComposeScreen)
    logcat(TAG, LogPriority.VERBOSE) { "pushScreen(${newComposeScreen.screenKey.key})" }
    newComposeScreen.onCreated()

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = emptyList()
    )

    return true
  }

  open fun popScreen(newComposeScreen: ComposeScreen): Boolean {
    return popScreen(newComposeScreen, true)
  }

  open fun popScreen(newComposeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    val indexOfPrev = _navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == newComposeScreen.screenKey }

    if (indexOfPrev < 0) {
      // Already removed
      return false
    }

    _navigationScreensStack.removeAt(indexOfPrev)

    val newScreenUpdate = if (withAnimation) {
      ScreenUpdate.Pop(newComposeScreen)
    } else {
      ScreenUpdate.Remove(newComposeScreen)
    }

    val navigationScreenUpdates = combineScreenUpdates(
      oldScreens = _navigationScreensStack,
      newScreenUpdate = newScreenUpdate
    )

    logcat(TAG, LogPriority.VERBOSE) { "popScreen(${newComposeScreen.screenKey.key})" }
    newComposeScreen.onStartDisposing()

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

  fun getRouterByKey(screenKey: ScreenKey): NavigationRouter {
    if (routerKey == screenKey) {
      return this
    }

    if (parentRouter != null) {
      val router = parentRouter.getRouterByKeyOrNull(screenKey)
      if (router != null) {
        return router
      }
    }

    error("NavigationRouter with key \'${screenKey.key}\' not found")
  }

  fun getRouterByKeyOrNull(screenKey: ScreenKey): NavigationRouter? {
    if (routerKey == screenKey) {
      return this
    }

    if (parentRouter != null) {
      return parentRouter.getRouterByKeyOrNull(screenKey)
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

  fun onNewIntent(intent: Intent) {
    _intentsFlow.tryEmit(intent)

    for (childRouter in childRouters.values) {
      childRouter.onNewIntent(intent)
    }
  }

  open suspend fun onScreenUpdateFinished(screenUpdate: ScreenUpdate) {
    if (!screenUpdate.isScreenBeingRemoved()) {
      return
    }

    screenUpdate.screen.onDisposed()

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