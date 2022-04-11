package com.github.k1rakishou.kurobaexlite.navigation

import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeBackgroundScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.main.MainScreen

class MainNavigationRouter : NavigationRouter(
  routerKey = MainScreen.SCREEN_KEY,
  parentRouter = null
) {
  private val floatingScreensStack = mutableListOf<FloatingComposeScreen>()

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

  override fun popScreen(newComposeScreen: ComposeScreen): Boolean {
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

  override fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
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

  override fun stopPresentingScreen(screenKey: ScreenKey): Boolean {
    val index = floatingScreensStack.indexOfLast { screen -> screen.screenKey == screenKey }
    if (index < 0) {
      return false
    }

    val floatingComposeScreen = floatingScreensStack.removeAt(index)

    val navigationScreenUpdates = navigationScreensStack
      .map { prevComposeScreen -> ScreenUpdate.Set(prevComposeScreen) }
    val floatingScreenUpdates = combineScreenUpdates(
      oldScreens = floatingScreensStack,
      newScreenUpdate = ScreenUpdate.Pop(floatingComposeScreen)
    ).toMutableList()

    if (floatingScreensStack.size <= 1 && floatingScreensStack.any { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }) {
      logcat.logcat(tag = TAG) { "stopPresentingScreen(${floatingComposeScreen.screenKey.key}) removing bgScreen" }

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

    logcat.logcat(tag = TAG) { "stopPresentingScreen(${floatingComposeScreen.screenKey.key})" }

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

    screenDestroyCallbacks.forEach { callback -> callback(screenUpdate.screen.screenKey) }

    if (navigationScreensStack.isEmpty() && floatingScreensStack.isEmpty()) {
      _screenUpdatesFlow.value = null
    }
  }

  companion object {
    private const val TAG = "MainNavigationRouter"
  }

}