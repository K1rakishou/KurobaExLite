package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RootRouterHost
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import org.koin.java.KoinJavaComponent.inject

class MainScreen(
  componentActivity: ComponentActivity,
  private val rootNavigationRouter: NavigationRouter
) : ComposeScreen(componentActivity) {
  private val uiInfoManager by inject<UiInfoManager>(UiInfoManager::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current

    Surface(
      modifier = Modifier.fillMaxSize(),
      color = chanTheme.backColorCompose
    ) {
      BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
      ) {
        val availableWidth = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val availableHeight = with(LocalDensity.current) { maxHeight.toPx().toInt() }
        uiInfoManager.updateMaxParentSize(availableWidth, availableHeight)

        PushScreenOnce(
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
          }
        )
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MainScreen")
  }
}