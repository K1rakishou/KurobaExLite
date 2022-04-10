package com.github.k1rakishou.kurobaexlite.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import com.github.k1rakishou.kurobaexlite.helpers.unreachable
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

open class NavigationRouter(
  val routerKey: ScreenKey,
  private val parentRouter: NavigationRouter?
) {
  val navigationScreensStack = mutableStateListOf<ComposeScreen>()
  protected val childRouters = linkedMapOf<ScreenKey, NavigationRouter>()
  protected val backPressHandlers = mutableListOf<OnBackPressHandler>()

  protected val _screenUpdatesFlow = MutableStateFlow<ScreenUpdateTransaction?>(null)
  val screenUpdatesFlow: StateFlow<ScreenUpdateTransaction?>
    get() = _screenUpdatesFlow.asStateFlow()

  private val _intentsFlow = MutableSharedFlow<Intent>(extraBufferCapacity = Channel.UNLIMITED)
  val intentsFlow: SharedFlow<Intent>
    get() = _intentsFlow.asSharedFlow()

  @Composable
  fun HandleBackPresses(onBackPressed: suspend () -> Boolean) {
    DisposableEffect(
      key1 = Unit,
      effect = {
        val handler = object : OnBackPressHandler() {
          override suspend fun onBackPressed(): Boolean {
            return onBackPressed()
          }
        }

        backPressHandlers += handler
        onDispose { backPressHandlers -= handler }
      }
    )
  }

  open fun pushScreen(newComposeScreen: ComposeScreen): Boolean {
    if (newComposeScreen is FloatingComposeScreen) {
      error("FloatingComposeScreens must be added via presentScreen() function!")
    }

    val indexOfPrev = navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == newComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return false
    }

    val navigationScreenUpdates = combineScreenUpdates(
      oldScreens = navigationScreensStack,
      newScreenUpdate = ScreenUpdate.Push(newComposeScreen)
    )

    navigationScreensStack.add(newComposeScreen)
    logcat.logcat(tag = TAG) { "pushScreen(${newComposeScreen.screenKey.key})" }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = emptyList()
    )

    return true
  }

  open fun popScreen(newComposeScreen: ComposeScreen): Boolean {
    val indexOfPrev = navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == newComposeScreen.screenKey }

    if (indexOfPrev < 0) {
      // Already removed
      return false
    }

    navigationScreensStack.removeAt(indexOfPrev)

    val navigationScreenUpdates = combineScreenUpdates(
      oldScreens = navigationScreensStack,
      newScreenUpdate = ScreenUpdate.Pop(newComposeScreen)
    )

    logcat.logcat(tag = TAG) { "popScreen(${newComposeScreen.screenKey.key})" }

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

  open fun stopPresentingScreen(screenKey: ScreenKey): Boolean {
    if (parentRouter != null) {
      return parentRouter.stopPresentingScreen(screenKey)
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

  fun requireParentRouter(): NavigationRouter {
    return requireNotNull(parentRouter) { "Parent router is null" }
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

  private fun getRouterByKeyOrNull(screenKey: ScreenKey): NavigationRouter? {
    if (routerKey == screenKey) {
      return this
    }

    if (parentRouter != null) {
      return parentRouter.getRouterByKeyOrNull(screenKey)
    }

    return null
  }

  fun onNewIntent(intent: Intent) {
    _intentsFlow.tryEmit(intent)

    for (childRouter in childRouters.values) {
      childRouter.onNewIntent(intent)
    }
  }

  suspend fun onBackPressed(): Boolean {
    if (childRouters.isNotEmpty()) {
      for ((_, navigationRouter) in childRouters.entries) {
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

  fun onDestroy() {
    childRouters.values.forEach { childRouter -> childRouter.onDestroy() }
  }

  fun isInsideScreen(lookupScreenKey: ScreenKey): Boolean {
    if (navigationScreensStack.any { composeScreen -> composeScreen.screenKey == lookupScreenKey }) {
      return true
    }

    if (parentRouter == null) {
      return false
    }

    return parentRouter.isInsideScreen(lookupScreenKey)
  }

  open fun onScreenUpdateFinished(screenUpdate: ScreenUpdate) {
    if (!screenUpdate.isScreenBeingRemoved()) {
      return
    }

    if (navigationScreensStack.isEmpty()) {
      _screenUpdatesFlow.value = null
    }
  }

  data class ScreenUpdateTransaction(
    val navigationScreenUpdates: List<ScreenUpdate>,
    val floatingScreenUpdates: List<ScreenUpdate>
  )

  sealed class ScreenUpdate(val screen: ComposeScreen) {

    fun isScreenBeingRemoved(): Boolean {
      return when (this) {
        is Fade -> this.fadeType == FadeType.Out
        is Pop -> true
        is Push -> false
        is Set -> false
      }
    }
    
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

  abstract class OnBackPressHandler {
    abstract suspend fun onBackPressed(): Boolean
  }

  companion object {
    private const val TAG = "NavigationRouter"
  }

}