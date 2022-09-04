package com.github.k1rakishou.kurobaexlite.features.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreen
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

class MainScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  private val mainNavigationRouter: MainNavigationRouter
) : ComposeScreen(screenArgs, componentActivity, mainNavigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val homeScreen = remember {
      val prevHomeScreen = navigationRouter.getScreenByKey(HomeScreen.SCREEN_KEY)
      if (prevHomeScreen != null) {
        return@remember prevHomeScreen as HomeScreen
      }

      return@remember ComposeScreen.createScreen<HomeScreen>(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(HomeScreen.SCREEN_KEY)
      )
    }

    ContentInternal(
      screenKey = screenKey,
      homeScreenProvider = { homeScreen },
      navigationRouterProvider = { navigationRouter },
      handleBackPresses = {
        mainNavigationRouter.HandleBackPresses {
          // First, process all the floating screens
          for (floatingComposeScreen in mainNavigationRouter.floatingScreensStack.asReversed()) {
            if (floatingComposeScreen.ignoreBackPresses) {
              continue
            }

            if (floatingComposeScreen.onBackPressed()) {
              return@HandleBackPresses true
            }
          }

          // Then process regular screens
          return@HandleBackPresses homeScreen.onBackPressed()
        }
      }
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MainScreen")
  }
}

@Composable
private fun ContentInternal(
  screenKey: ScreenKey,
  homeScreenProvider: () -> HomeScreen,
  navigationRouterProvider: () -> NavigationRouter,
  handleBackPresses: @Composable () -> Unit
) {
  val insets = LocalWindowInsets.current

  val mediaSaver: MediaSaver = koinRemember()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val contentPadding = remember(
    key1 = insets.left,
    key2 = insets.right
  ) { PaddingValues(start = insets.left, end = insets.right) }

  var contentSize by remember { mutableStateOf(IntSize.Zero) }
  val kurobaSnackbarState = rememberKurobaSnackbarState()

  LaunchedEffect(
    key1 = Unit,
    block = {
      mediaSaver.activeDownloadsInfoFlow.collect { activeDownloadsInfo ->
        if (activeDownloadsInfo == null) {
          kurobaSnackbarState.popSnackbar(SnackbarId.ActiveDownloadsInfo)
          return@collect
        }

        kurobaSnackbarState.pushSnackbar(
          SnackbarInfo(
            snackbarId = SnackbarId.ActiveDownloadsInfo,
            aliveUntil = null,
            content = listOf(
              SnackbarContentItem.LoadingIndicator,
              SnackbarContentItem.Spacer(12.dp),
              SnackbarContentItem.Text(activeDownloadsInfo)
            )
          )
        )
      }
    })

  handleBackPresses()

  GradientBackground(
    modifier = Modifier
      .fillMaxSize()
      .padding(contentPadding)
      .onSizeChanged { size -> contentSize = size }
  ) {
    if (contentSize.width > 0 && contentSize.height > 0) {
      val availableWidth = contentSize.width
      val availableHeight = contentSize.height

      globalUiInfoManager.updateMaxParentSize(
        availableWidth = availableWidth,
        availableHeight = availableHeight
      )

      RouterHost(
        navigationRouter = navigationRouterProvider(),
        defaultScreenFunc = homeScreenProvider
      )

      KurobaSnackbarContainer(
        modifier = Modifier.fillMaxSize(),
        screenKey = screenKey,
        isTablet = globalUiInfoManager.isTablet,
        kurobaSnackbarState = kurobaSnackbarState
      )
    }
  }
}