package com.github.k1rakishou.kurobaexlite.ui.screens.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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

    val contentPadding = remember(
      key1 = insets.left,
      key2 = insets.right
    ) { PaddingValues(start = insets.left, end = insets.right) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val kurobaSnackbarState = rememberKurobaSnackbarState(snackbarManager = snackbarManager)

    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .onSizeChanged { size -> contentSize = size },
      color = chanTheme.backColorCompose
    ) {
      if (contentSize.width > 0 && contentSize.height > 0) {
        val availableWidth = contentSize.width
        val availableHeight = contentSize.height

        uiInfoManager.updateMaxParentSize(availableWidth, availableHeight)

        PushScreen(
          navigationRouter = navigationRouter,
          composeScreenBuilder = {
            HomeScreen(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter.childRouter(HomeScreen.SCREEN_KEY)
            )
          }
        )

        RootRouterHost(navigationRouter)

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

  companion object {
    val SCREEN_KEY = ScreenKey("MainScreen")
  }
}