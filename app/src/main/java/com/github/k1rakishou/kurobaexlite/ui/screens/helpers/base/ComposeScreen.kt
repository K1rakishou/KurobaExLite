package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import org.koin.java.KoinJavaComponent.inject

abstract class ComposeScreen(
  protected val componentActivity: ComponentActivity,
  protected val navigationRouter: NavigationRouter
) {
  protected val globalConstants by inject<GlobalConstants>(GlobalConstants::class.java)
  protected val uiInfoManager by inject<UiInfoManager>(UiInfoManager::class.java)

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

}