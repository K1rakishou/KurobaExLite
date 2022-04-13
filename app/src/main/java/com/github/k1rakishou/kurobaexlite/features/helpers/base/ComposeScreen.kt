package com.github.k1rakishou.kurobaexlite.features.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.features.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import org.koin.android.ext.android.inject
import org.koin.java.KoinJavaComponent.inject

abstract class ComposeScreen(
  val componentActivity: ComponentActivity,
  val navigationRouter: NavigationRouter
) {
  protected val globalConstants: GlobalConstants by inject(GlobalConstants::class.java)
  protected val uiInfoManager: UiInfoManager by inject(UiInfoManager::class.java)
  protected val appSettings: AppSettings by inject(AppSettings::class.java)
  protected val snackbarManager: SnackbarManager by componentActivity.inject()

  private val backPressHandlers = mutableListOf<MainNavigationRouter.OnBackPressHandler>()

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

    return navigationRouter.requireParentRouter().popScreen(this)
  }

  suspend fun onBackPressed(): Boolean {
    for (backPressHandler in backPressHandlers) {
      if (backPressHandler.onBackPressed()) {
        return true
      }
    }

    return false
  }

}