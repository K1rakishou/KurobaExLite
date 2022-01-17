package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RootRouterHost
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey

class MainScreen(
  componentActivity: ComponentActivity
) : ComposeScreen(componentActivity) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val rootNavigationRouter = remember {
      NavigationRouter(
        routerIndex = null,
        parentRouter = null
      )
    }

    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color.LightGray
    ) {
      pushScreenOnce(
        navigationRouter = rootNavigationRouter,
        composeScreenBuilder = { HomeScreen(componentActivity) }
      )

      RootRouterHost(
        componentActivity = componentActivity,
        rootNavigationRouter = rootNavigationRouter,
        onTransitionFinished = { executedScreenUpdate ->
          if (executedScreenUpdate.isPop() && !rootNavigationRouter.hasScreens()) {
            componentActivity.finish()
          }
        })
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MainScreen")
  }
}