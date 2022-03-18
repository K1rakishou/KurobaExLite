package com.github.k1rakishou.kurobaexlite.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeBackgroundScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationRouter(
  val routerKey: String,
  private val parentRouter: NavigationRouter?
) {
  private val navigationScreensStack = mutableListOf<ComposeScreen>()
  private val floatingScreensStack = mutableListOf<FloatingComposeScreen>()
  private val childRouters = linkedMapOf<String, NavigationRouter>()
  private val backPressHandlers = mutableListOf<OnBackPressHandler>()

  private val _screenUpdatesFlow = MutableStateFlow<ScreenUpdateTransaction?>(null)
  val screenUpdatesFlow: StateFlow<ScreenUpdateTransaction?>
    get() = _screenUpdatesFlow.asStateFlow()

  private val _intentsFlow = MutableSharedFlow<Intent>(extraBufferCapacity = Channel.UNLIMITED)
  val intentsFlow: SharedFlow<Intent>
    get() = _intentsFlow.asSharedFlow()

  @Composable
  fun HandleBackPresses(screenKey: ScreenKey, onBackPressed: suspend () -> Boolean) {
    DisposableEffect(
      key1 = Unit,
      effect = {
        val handler = object : OnBackPressHandler(screenKey) {
          override suspend fun onBackPressed(): Boolean {
            return onBackPressed()
          }
        }

        backPressHandlers += handler
        onDispose { backPressHandlers -= handler }
      }
    )
  }

  fun pushScreen(newComposeScreen: ComposeScreen): Boolean {
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
    val floatingScreenUpdates = floatingScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }

    navigationScreensStack.add(newComposeScreen)
    logcat.logcat(tag = TAG) { "pushScreen(${newComposeScreen.screenKey.key})" }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )

    return true
  }

  fun popScreen(newComposeScreen: ComposeScreen): Boolean {
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
    val floatingScreenUpdates = floatingScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }

    logcat.logcat(tag = TAG) { "popScreen(${newComposeScreen.screenKey.key})" }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )

    return true
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
    ).toMutableList()

    if (floatingScreensStack.none { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }) {
      logcat.logcat(tag = TAG) { "presentScreen(${floatingComposeScreen.screenKey.key}) adding bgScreen" }

      val bgScreen = FloatingComposeBackgroundScreen(
        componentActivity = floatingComposeScreen.componentActivity,
        navigationRouter = floatingComposeScreen.navigationRouter
      )

      floatingScreenUpdates.add(0, ScreenUpdate.Fade(bgScreen, ScreenUpdate.FadeType.In))
      floatingScreensStack.add(0, bgScreen)
    }

    floatingScreensStack.add(floatingComposeScreen)
    logcat.logcat(tag = TAG) { "presentScreen(${floatingComposeScreen.screenKey.key})" }

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
    ).toMutableList()

    if (floatingScreensStack.size <= 1 && floatingScreensStack.any { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }) {
      logcat.logcat(tag = TAG) { "unpresentScreen(${floatingComposeScreen.screenKey.key}) removing bgScreen" }

      val indexOfPrevBg = floatingScreenUpdates
        .indexOfFirst { screenUpdate -> screenUpdate.screen.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }
      if (indexOfPrevBg >= 0) {
        floatingScreenUpdates.removeAt(indexOfPrevBg)
      }

      val bgScreen = FloatingComposeBackgroundScreen(
        componentActivity = floatingComposeScreen.componentActivity,
        navigationRouter = floatingComposeScreen.navigationRouter
      )

      floatingScreenUpdates.add(0, ScreenUpdate.Fade(bgScreen, ScreenUpdate.FadeType.Out))

      val indexOfBgScreen = floatingScreensStack
        .indexOfLast { screen -> screen.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }
      if (indexOfBgScreen >= 0) {
        floatingScreensStack.removeAt(indexOfBgScreen)
      }
    }

    logcat.logcat(tag = TAG) { "stopPresenting(${floatingComposeScreen.screenKey.key})" }

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

  fun childRouter(key: String): NavigationRouter {
    return childRouters.getOrPut(
      key = key,
      defaultValue = {
        NavigationRouter(
          routerKey = key,
          parentRouter = this
        )
      }
    )
  }

  fun getRouterByKey(key: String): NavigationRouter {
    if (routerKey == key) {
      return this
    }

    if (parentRouter != null) {
      val router = parentRouter.getRouterByKeyOrNull(key)
      if (router != null) {
        return router
      }
    }

    error("NavigationRouter with key \'$key\' not found")
  }

  private fun getRouterByKeyOrNull(key: String): NavigationRouter? {
    if (routerKey == key) {
      return this
    }

    if (parentRouter != null) {
      return parentRouter.getRouterByKeyOrNull(key)
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

  abstract class OnBackPressHandler(
    val screenKey: ScreenKey
  ) {
    abstract suspend fun onBackPressed(): Boolean
  }

  companion object {
    private const val TAG = "NavigationRouter"
  }

}