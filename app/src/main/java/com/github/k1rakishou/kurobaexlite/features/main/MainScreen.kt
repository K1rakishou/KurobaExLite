package com.github.k1rakishou.kurobaexlite.features.main

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
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreen
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RootRouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

class MainScreen(
  componentActivity: ComponentActivity,
  private val mainNavigationRouter: MainNavigationRouter
) : ComposeScreen(componentActivity, mainNavigationRouter) {
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
    val kurobaSnackbarState = rememberKurobaSnackbarState()

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

        val homeScreen = remember {
          val prevHomeScreen = navigationRouter.getScreenByKey(HomeScreen.SCREEN_KEY)
          if (prevHomeScreen != null) {
            return@remember prevHomeScreen
          }

          return@remember HomeScreen(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter.childRouter(HomeScreen.SCREEN_KEY)
          )
        }

        PushScreen(
          navigationRouter = navigationRouter,
          composeScreenBuilder = { homeScreen }
        )

        mainNavigationRouter.HandleBackPresses {
          // First, process all the floating screens
          for (floatingComposeScreen in mainNavigationRouter.floatingScreensStack.asReversed()) {
            if (floatingComposeScreen.onBackPressed()) {
              return@HandleBackPresses true
            }
          }

          // Then process regular screens
          return@HandleBackPresses homeScreen.onBackPressed()
        }

        RootRouterHost(navigationRouter)

        KurobaSnackbarContainer(
          modifier = Modifier.fillMaxSize(),
          screenKey = screenKey,
          kurobaSnackbarState = kurobaSnackbarState
        )
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MainScreen")
  }
}