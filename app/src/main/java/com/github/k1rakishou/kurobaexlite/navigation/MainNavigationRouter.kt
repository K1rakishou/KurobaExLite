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

  override fun screenExistsInThisRouter(screenKey: ScreenKey): Boolean {
    val exists = floatingScreensStack
      .any { composeScreen -> composeScreen.screenKey == screenKey }

    if (exists) {
      return true
    }

    return super.screenExistsInThisRouter(screenKey)
  }

  override fun onLifecycleCreate() {
    super.onLifecycleCreate()

    _floatingScreensStack.forEach { screen ->
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Creating, true)
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Created, true)
    }
  }

  override fun onLifecycleDestroy() {
    super.onLifecycleDestroy()

    _floatingScreensStack.forEach { screen ->
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposing, true)
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposed, true)
    }
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
      // Already added, update the arguments
      navigationScreensStack[indexOfPrev].onNewArguments(composeScreen.screenArgs)
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
    composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Creating)

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
    composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposing)

    Snapshot.withMutableSnapshot {
      _screenAnimations.put(composeScreen.screenKey, screenAnimation)
    }

    return true
  }

  override fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
    val indexOfPrev = _floatingScreensStack
      .indexOfFirst { screen -> screen.screenKey == floatingComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added, update the arguments
      _floatingScreensStack[indexOfPrev].onNewArguments(floatingComposeScreen.screenArgs)
      return
    }

    if (
      !floatingComposeScreen.customBackground &&
      _floatingScreensStack.none { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }
    ) {
      logcat(TAG, LogPriority.VERBOSE) {
        "presentScreen(${FloatingComposeBackgroundScreen.SCREEN_KEY}) at 0"
      }

      val bgScreen = FloatingComposeBackgroundScreen(
        componentActivity = floatingComposeScreen.componentActivity,
        navigationRouter = floatingComposeScreen.navigationRouter
      )
      bgScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Creating)

      Snapshot.withMutableSnapshot {
        _floatingScreensStack.add(0, bgScreen)
        _screenAnimations.put(
          bgScreen.screenKey,
          ScreenAnimation.Fade(bgScreen.screenKey, ScreenAnimation.FadeType.In)
        )
      }
    }

    _floatingScreensStack.add(floatingComposeScreen)
    logcat(TAG, LogPriority.VERBOSE) { "presentScreen(${floatingComposeScreen.screenKey.key}) at 0" }

    _screenAnimations.put(
      floatingComposeScreen.screenKey,
      ScreenAnimation.Fade(floatingComposeScreen.screenKey, ScreenAnimation.FadeType.In)
    )

    floatingComposeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Creating)
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

    if (shouldRemoveBgScreen(screenKey)) {
      val indexOfBgScreen = floatingScreensStack
        .indexOfFirst { floatingScreen -> floatingScreen.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }

      logcat(TAG, LogPriority.VERBOSE) {
        "stopPresentingScreen(${FloatingComposeBackgroundScreen.SCREEN_KEY}) at $indexOfBgScreen"
      }

      val prevBgScreen = floatingScreensStack.getOrNull(indexOfBgScreen)
      if (prevBgScreen != null) {
        prevBgScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposing)

        _screenAnimations.put(
          prevBgScreen.screenKey,
          ScreenAnimation.Fade(prevBgScreen.screenKey, ScreenAnimation.FadeType.Out)
        )
      }
    }

    logcat(TAG, LogPriority.VERBOSE) {
      "stopPresentingScreen(${floatingComposeScreen.screenKey.key}) at $index"
    }
    floatingComposeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposing)

    _screenAnimations.put(
      screenKey,
      overrideAnimation ?: ScreenAnimation.Fade(screenKey, ScreenAnimation.FadeType.Out)
    )

    return true
  }

  private fun shouldRemoveBgScreen(unpresentedScreenKey: ScreenKey): Boolean {
    // If there is already an animation with the background screen then do nothing
    if (_screenAnimations.containsKey(FloatingComposeBackgroundScreen.SCREEN_KEY)) {
      return false
    }

    // Only check screens that do not handle the background on their own (Do not use the
    // FloatingComposeBackgroundScreen) AND screens that are not the screen that we are about to
    // destroy
    val remainingScreens = _floatingScreensStack.filter { floatingScreen ->
      return@filter !floatingScreen.customBackground &&
        floatingScreen.screenKey != unpresentedScreenKey
    }

    if (remainingScreens.size <= 1) {
      return remainingScreens.any { it.screenKey == FloatingComposeBackgroundScreen.SCREEN_KEY }
    }

    return false
  }

  override suspend fun onScreenAnimationFinished(screenAnimation: ScreenAnimation) {
    val prevScreenAnimation = _screenAnimations[screenAnimation.screenKey]

    if (prevScreenAnimation != null && prevScreenAnimation !== screenAnimation) {
      // During the animation the stack was changed so the ScreenAnimation was replaced with a
      // different one. In this case we don't need to do anything
      return
    }

    _screenAnimations.remove(screenAnimation.screenKey)

    val composeScreen = floatingScreensStack
      .firstOrNull { composeScreen -> composeScreen.screenKey == screenAnimation.screenKey }

    if (composeScreen == null) {
      return
    }

    if (!screenAnimation.isScreenBeingRemoved()) {
      composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Created)
      return
    }

    composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposed)

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