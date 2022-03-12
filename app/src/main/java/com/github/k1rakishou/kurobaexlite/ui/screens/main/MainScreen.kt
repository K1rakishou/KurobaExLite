package com.github.k1rakishou.kurobaexlite.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RootRouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeScreen

class MainScreen(
  componentActivity: ComponentActivity,
  rootNavigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, rootNavigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val insets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current

    Surface(
      modifier = Modifier.fillMaxSize(),
      color = chanTheme.backColorCompose
    ) {
      val contentPadding = remember(key1 = insets.left, key2 = insets.right) {
        PaddingValues(start = insets.left, end = insets.right)
      }
      val kurobaSnackbarState = rememberKurobaSnackbarState(snackbarManager = snackbarManager)

      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
          .padding(contentPadding)
      ) {
        val availableWidth = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val availableHeight = with(LocalDensity.current) { maxHeight.toPx().toInt() }

        if (availableWidth > 0 && availableHeight > 0) {
          uiInfoManager.updateMaxParentSize(availableWidth, availableHeight)

          PushScreen(
            navigationRouter = navigationRouter,
            composeScreenBuilder = {
              HomeScreen(
                componentActivity = componentActivity,
                navigationRouter = navigationRouter.childRouter(HomeScreen.SCREEN_KEY.key)
              )
            }
          )

          RootRouterHost(screenKey, navigationRouter)

          KurobaSnackbarContainer(
            modifier = Modifier.fillMaxSize(),
            screenKey = screenKey,
            uiInfoManager = uiInfoManager,
            snackbarManager = snackbarManager,
            kurobaSnackbarState = kurobaSnackbarState
          )
        }
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MainScreen")
  }
}