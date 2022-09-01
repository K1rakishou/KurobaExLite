package com.github.k1rakishou.kurobaexlite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
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

    return super.pushScreen(composeScreen, withAnimation)
  }

  override fun popScreen(composeScreen: ComposeScreen): Boolean {
    return popScreen(composeScreen, true)
  }

  override fun popScreen(composeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    if (composeScreen is FloatingComposeScreen) {
      error("FloatingComposeScreens must be removed via stopPresentingScreen() function!")
    }

    return super.popScreen(composeScreen, withAnimation)
  }

  override fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
    val indexOfPrev = _floatingScreensStack
      .indexOfFirst { screen -> screen.screenKey == floatingComposeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added, update the arguments
      _floatingScreensStack[indexOfPrev].onNewArguments(floatingComposeScreen.screenArgs)
      return
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