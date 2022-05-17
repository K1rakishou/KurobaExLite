package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

abstract class ComposeScreen(
  val componentActivity: ComponentActivity,
  val navigationRouter: NavigationRouter
) {
  protected val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  protected val globalConstants: GlobalConstants by inject(GlobalConstants::class.java)
  protected val appSettings: AppSettings by inject(AppSettings::class.java)
  protected val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)

  private val backPressHandlers = mutableListOf<MainNavigationRouter.OnBackPressHandler>()
  protected val screenCoroutineScope = KurobaCoroutineScope()

  abstract val screenKey: ScreenKey

  @Composable
  fun HandleBackPresses(backPressHandler: MainNavigationRouter.OnBackPressHandler) {
    DisposableEffect(
      key1 = Unit,
      effect = {
        backPressHandlers += backPressHandler
        onDispose { backPressHandlers -= backPressHandler }
      }
    )
  }

  /**
   * [onStartCreating] is called when the screen is added to the stack before the animation started
   * playing
   * */
  @CallSuper
  open fun onStartCreating() {
    logcat(TAG, LogPriority.VERBOSE) { "onStartCreating(${screenKey.key})" }
  }

  /**
   * [onCreated] is called after the creation animation finished playing
   * */
  @CallSuper
  open fun onCreated() {
    logcat(TAG, LogPriority.VERBOSE) { "onCreated(${screenKey.key})" }
  }

  /**
   * [onStartDisposing] is called when the screen is removed from the stack before the animation
   * started playing
   * */
  @CallSuper
  open fun onStartDisposing() {
    logcat(TAG, LogPriority.VERBOSE) { "onStartDisposing(${screenKey.key})" }
  }

  /**
   * [onCreated] is called after the disposing animation finished playing
   * */
  @CallSuper
  open fun onDisposed() {
    logcat(TAG, LogPriority.VERBOSE) { "onDisposed(${screenKey.key})" }
    screenCoroutineScope.cancelChildren()
  }

  @Composable
  abstract fun Content()

  @Composable
  protected fun PushScreen(
    navigationRouter: NavigationRouter,
    composeScreenBuilder: () -> ComposeScreen
  ) {
    LaunchedEffect(
      key1 = Unit,
      block = { navigationRouter.pushScreen(composeScreenBuilder()) }
    )
  }

  protected fun popScreen(): Boolean {
    if (this is FloatingComposeScreen) {
      error("Can't pop FloatingComposeScreen, use stopPresenting()")
    }

    return navigationRouter.popScreen(this)
  }

  suspend fun onBackPressed(): Boolean {
    for (backPressHandler in backPressHandlers) {
      if (backPressHandler.onBackPressed()) {
        return true
      }
    }

    return false
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ComposeScreen

    if (screenKey != other.screenKey) return false

    return true
  }

  override fun hashCode(): Int {
    return screenKey.hashCode()
  }

  companion object {
    private const val TAG = "ComposeScreen"
  }

}