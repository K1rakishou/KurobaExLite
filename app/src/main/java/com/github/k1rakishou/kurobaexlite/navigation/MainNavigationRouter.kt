package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeBackgroundScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.MinimizableFloatingComposeScreen
import logcat.LogPriority
import logcat.logcat

class MainNavigationRouter : NavigationRouter(
  routerKey = MainScreen.SCREEN_KEY,
  parentRouter = null
) {
  private val _floatingScreensStack = mutableStateListOf<FloatingComposeScreen>()
  val floatingScreensStack: List<FloatingComposeScreen>
    get() = _floatingScreensStack

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

  override fun pushScreen(composeScreen: ComposeScreen): Boolean {
    return pushScreen(composeScreen, true)
  }

  override fun pushScreen(composeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    if (composeScreen is FloatingComposeScreen) {
      error("FloatingComposeScreens must be added via presentScreen() function!")
    }

    val indexOfPrev = navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == composeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added
      return false
    }

    val screenAnimation = if (withAnimation) {
      ScreenAnimation.Push(composeScreen.screenKey)
    } else {
      ScreenAnimation.Set(composeScreen.screenKey)
    }

    Snapshot.withMutableSnapshot {
      _navigationScreensStack.add(composeScreen)
      _screenAnimations.put(composeScreen.screenKey, screenAnimation)
    }

    logcat(TAG, LogPriority.VERBOSE) { "pushScreen(${composeScreen.screenKey.key})" }
    composeScreen.onStartCreating()

    return true
  }

  override fun popScreen(composeScreen: ComposeScreen): Boolean {
    return popScreen(composeScreen, true)
  }

  override fun popScreen(composeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    val indexOfPrev = navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == composeScreen.screenKey }

    if (indexOfPrev < 0) {
      // Already removed
      return false
    }

    if (!removingScreens.add(composeScreen.screenKey)) {
      return false
    }

    val screenAnimation = if (withAnimation) {
      ScreenAnimation.Pop(composeScreen.screenKey)
    } else {
      ScreenAnimation.Remove(composeScreen.screenKey)
    }

    logcat(TAG, LogPriority.VERBOSE) { "popScreen(${composeScreen.screenKey.key})" }
    composeScreen.onStartDisposing()

    Snapshot.withMutableSnapshot {
      _screenAnimations.put(composeScreen.screenKey, screenAnimation)
    }

    return true
  }

  override fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
    val indexOfPrev = _floatingScreensStack
      .indexOfFirst { screen -> screen.screenKey == floatingComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // TODO(KurobaEx): pass screenArgs from floatingComposeScreen into the same screen
      //  that is already in the stack
      if (floatingComposeScreen is MinimizableFloatingComposeScreen) {
        floatingComposeScreen.maximize()
      }

      // Already added
      return
    }

    if (
      !floatingComposeScreen.customBackground &&
      _floatingScreensStack.none { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }
    ) {
      logcat(TAG, LogPriority.VERBOSE) { "presentScreen(${floatingComposeScreen.screenKey.key}) adding bgScreen" }

      val bgScreen = FloatingComposeBackgroundScreen(
        componentActivity = floatingComposeScreen.componentActivity,
        navigationRouter = floatingComposeScreen.navigationRouter
      )

      Snapshot.withMutableSnapshot {
        _floatingScreensStack.add(0, bgScreen)
        _screenAnimations.put(
          bgScreen.screenKey,
          ScreenAnimation.Fade(bgScreen.screenKey, ScreenAnimation.FadeType.In)
        )
      }
    }

    _floatingScreensStack.add(floatingComposeScreen)
    logcat(TAG, LogPriority.VERBOSE) { "presentScreen(${floatingComposeScreen.screenKey.key})" }
    floatingComposeScreen.onStartCreating()
  }

  override fun stopPresentingScreen(
    screenKey: ScreenKey,
    overrideAnimation: ScreenAnimation?
  ): Boolean {
    val index = _floatingScreensStack.indexOfLast { screen -> screen.screenKey == screenKey }
    if (index < 0) {
      return false
    }

    if (!removingScreens.add(screenKey)) {
      return false
    }

    val floatingComposeScreen = floatingScreensStack.getOrNull(index)
      ?: return false

    if (canRemoveBgScreenWhenUnpresenting()) {
      logcat(TAG, LogPriority.VERBOSE) { "stopPresentingScreen(${floatingComposeScreen.screenKey.key}) removing bgScreen" }

      val prevBgScreen = floatingScreensStack
        .firstOrNull { floatingScreen -> floatingScreen.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }

      if (prevBgScreen != null) {
        _screenAnimations.put(
          prevBgScreen.screenKey,
          ScreenAnimation.Fade(prevBgScreen.screenKey, ScreenAnimation.FadeType.Out)
        )
      }
    }

    logcat(TAG, LogPriority.VERBOSE) { "stopPresentingScreen(${floatingComposeScreen.screenKey.key})" }
    floatingComposeScreen.onStartDisposing()

    _screenAnimations.put(
      screenKey,
      overrideAnimation ?: ScreenAnimation.Fade(screenKey, ScreenAnimation.FadeType.Out)
    )

    return true
  }

  private fun canRemoveBgScreenWhenUnpresenting(): Boolean {
    if (_floatingScreensStack.size <= 1) {
      return _floatingScreensStack.any { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }
    }

    if (_floatingScreensStack.size == 2) {
      val hasBgScreen = _floatingScreensStack
        .indexOfFirst { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY } >= 0
      val hasScreenWithCustomBg = _floatingScreensStack
        .indexOfFirst { it.customBackground } >= 0

      if (hasBgScreen && !hasScreenWithCustomBg) {
        return true
      }
    }

    return false
  }

  override suspend fun onScreenAnimationFinished(screenAnimation: ScreenAnimation) {
    _screenAnimations.remove(screenAnimation.screenKey)

    val composeScreen = floatingScreensStack
      .firstOrNull { composeScreen -> composeScreen.screenKey == screenAnimation.screenKey }

    if (composeScreen == null) {
      return
    }

    if (!screenAnimation.isScreenBeingRemoved()) {
      return
    }

    composeScreen.onDisposed()

    _floatingScreensStack
      .indexOfFirst { screen -> screen.screenKey == screenAnimation.screenKey }
      .takeIf { index -> index >= 0 }
      ?.let { indexOfRemovedScreen ->
        removingScreens.remove(screenAnimation.screenKey)
        _floatingScreensStack.removeAt(indexOfRemovedScreen)
      }
  }

  suspend fun onBackBackPressed(): Boolean {
    for (backPressHandler in backPressHandlers) {
      if (backPressHandler.onBackPressed()) {
        return true
      }
    }

    return false
  }

  fun interface OnBackPressHandler {
    suspend fun onBackPressed(): Boolean
  }

  companion object {
    private const val TAG = "MainNavigationRouter"
  }

}