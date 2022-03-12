package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import org.koin.android.ext.android.inject
import org.koin.java.KoinJavaComponent.inject

abstract class ComposeScreen(
  val componentActivity: ComponentActivity,
  val navigationRouter: NavigationRouter
) {
  protected val globalConstants: GlobalConstants by inject(GlobalConstants::class.java)
  protected val uiInfoManager: UiInfoManager by inject(UiInfoManager::class.java)
  protected val snackbarManager: SnackbarManager by componentActivity.inject()

  abstract val screenKey: ScreenKey

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
    return navigationRouter.popScreen(this)
  }

}