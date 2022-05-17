package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeBackgroundScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import logcat.LogPriority
import logcat.logcat

class MainNavigationRouter : NavigationRouter(
  routerKey = MainScreen.SCREEN_KEY,
  parentRouter = null
) {
  private val _floatingScreensStack = mutableListOf<FloatingComposeScreen>()
  val floatingScreensStack: List<FloatingComposeScreen>
    get() = _floatingScreensStack.toList()

  protected val backPressHandlers = mutableListOf<OnBackPressHandler>()

  @Composable
  fun HandleBackPresses(onBackPressHandler: OnBackPressHandler) {
    DisposableEffect(
      key1 = Unit,
      effect = {
        backPressHandlers += onBackPressHandler
        onDispose { backPressHandlers -= onBackPressHandler }
      }
    )
  }

  override fun pushScreen(newComposeScreen: ComposeScreen): Boolean {
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
    val floatingScreenUpdates = _floatingScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }

    _navigationScreensStack.add(newComposeScreen)
    logcat(TAG, LogPriority.VERBOSE) { "pushScreen(${newComposeScreen.screenKey.key})" }
    newComposeScreen.onStartCreating()

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )

    return true
  }

  override fun popScreen(newComposeScreen: ComposeScreen): Boolean {
    return popScreen(newComposeScreen, true)
  }

  override fun popScreen(newComposeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    val indexOfPrev = navigationScreensStack
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
      oldScreens = navigationScreensStack,
      newScreenUpdate = newScreenUpdate
    )
    val floatingScreenUpdates = _floatingScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }

    logcat(TAG, LogPriority.VERBOSE) { "popScreen(${newComposeScreen.screenKey.key})" }

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )

    return true
  }

  override fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
    val indexOfPrev = _floatingScreensStack
      .indexOfFirst { screen -> screen.screenKey == floatingComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return
    }

    val navigationScreenUpdates = navigationScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }
    val floatingScreenUpdates = combineScreenUpdates(
      oldScreens = _floatingScreensStack,
      newScreenUpdate = chooseAddAnimation(floatingComposeScreen)
    ).toMutableList()

    if (_floatingScreensStack.none { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }) {
      logcat(TAG, LogPriority.VERBOSE) { "presentScreen(${floatingComposeScreen.screenKey.key}) adding bgScreen" }

      val bgScreen = FloatingComposeBackgroundScreen(
        componentActivity = floatingComposeScreen.componentActivity,
        navigationRouter = floatingComposeScreen.navigationRouter
      )

      floatingScreenUpdates.add(0, ScreenUpdate.Fade(bgScreen, ScreenUpdate.FadeType.In))
      _floatingScreensStack.add(0, bgScreen)
    }

    _floatingScreensStack.add(floatingComposeScreen)
    logcat(TAG, LogPriority.VERBOSE) { "presentScreen(${floatingComposeScreen.screenKey.key})" }
    floatingComposeScreen.onStartCreating()

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )
  }

  override fun stopPresentingScreen(
    screenKey: ScreenKey,
    overrideAnimation: ScreenRemoveAnimation?
  ): Boolean {
    val index = _floatingScreensStack.indexOfLast { screen -> screen.screenKey == screenKey }
    if (index < 0) {
      return false
    }

    val floatingComposeScreen = _floatingScreensStack.removeAt(index)

    val navigationScreenUpdates = navigationScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }
    val floatingScreenUpdates = combineScreenUpdates(
      oldScreens = _floatingScreensStack,
      newScreenUpdate = chooseRemoveAnimation(overrideAnimation, floatingComposeScreen)
    ).toMutableList()

    if (_floatingScreensStack.size <= 1 && _floatingScreensStack.any { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }) {
      logcat(TAG, LogPriority.VERBOSE) { "stopPresentingScreen(${floatingComposeScreen.screenKey.key}) removing bgScreen" }

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

      val indexOfBgScreen = _floatingScreensStack
        .indexOfLast { screen -> screen.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }
      if (indexOfBgScreen >= 0) {
        _floatingScreensStack.removeAt(indexOfBgScreen)
      }
    }

    logcat(TAG, LogPriority.VERBOSE) { "stopPresentingScreen(${floatingComposeScreen.screenKey.key})" }
    floatingComposeScreen.onStartDisposing()

    _screenUpdatesFlow.value = ScreenUpdateTransaction(
      navigationScreenUpdates = navigationScreenUpdates,
      floatingScreenUpdates = floatingScreenUpdates
    )

    return true
  }

  override suspend fun onScreenUpdateFinished(screenUpdate: ScreenUpdate) {
    if (!screenUpdate.isScreenBeingRemoved()) {
      return
    }

    screenUpdate.screen.onDisposed()

    if (navigationScreensStack.isEmpty() && floatingScreensStack.isEmpty()) {
      _screenUpdatesFlow.value = null
      return
    }

    removeUpdateAfterAnimationFinished(screenUpdate)
  }

  suspend fun onBackBackPressed(): Boolean {
    for (backPressHandler in backPressHandlers) {
      if (backPressHandler.onBackPressed()) {
        return true
      }
    }

    return false
  }

  private fun chooseAddAnimation(
    floatingComposeScreen: FloatingComposeScreen
  ): ScreenUpdate {
    return when (floatingComposeScreen.presentAnimation) {
      ScreenAddAnimation.FadeIn -> ScreenUpdate.Fade(floatingComposeScreen, ScreenUpdate.FadeType.In)
      ScreenAddAnimation.Push -> ScreenUpdate.Push(floatingComposeScreen)
    }
  }

  private fun chooseRemoveAnimation(
    overrideAnimation: ScreenRemoveAnimation?,
    floatingComposeScreen: FloatingComposeScreen
  ): ScreenUpdate {
    return when (overrideAnimation) {
      null,
      ScreenRemoveAnimation.FadeOut -> ScreenUpdate.Fade(floatingComposeScreen, ScreenUpdate.FadeType.Out)
      ScreenRemoveAnimation.Pop -> ScreenUpdate.Pop(floatingComposeScreen)
    }
  }

  fun interface OnBackPressHandler {
    suspend fun onBackPressed(): Boolean
  }

  companion object {
    private const val TAG = "MainNavigationRouter"
  }

}