package com.github.k1rakishou.kurobaexlite.features.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreen
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.Tuple4
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowSizeClass
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

val LocalMainUiLayoutMode = staticCompositionLocalOf<MainUiLayoutMode> { error("LocalMainUiLayoutMode not initialized") }

class MainScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  private val mainNavigationRouter: MainNavigationRouter
) : ComposeScreen(screenArgs, componentActivity, mainNavigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val homeScreen = remember {
      // This is needed here, if removed the backpress callbacks will stop working after phone rotation
      val prevHomeScreen = navigationRouter.getScreenByKey(HomeScreen.SCREEN_KEY)
      if (prevHomeScreen != null) {
        return@remember prevHomeScreen as HomeScreen
      }

      return@remember ComposeScreen.createScreen<HomeScreen>(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(HomeScreen.SCREEN_KEY)
      )
    }

    var globalUiInfoManagerInitialized by remember { mutableStateOf(false) }

    if (!globalUiInfoManagerInitialized) {
      globalUiInfoManager.init()
      globalUiInfoManagerInitialized = true
    }

    ProvideLocalMainUiLayoutMode {
      ContentInternal(
        screenKey = screenKey,
        homeScreenProvider = { homeScreen },
        navigationRouterProvider = { navigationRouter },
        handleBackPresses = {
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
        }
      )
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MainScreen")
  }
}

@Composable
private fun ProvideLocalMainUiLayoutMode(content: @Composable () -> Unit) {
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val appSettings: AppSettings = koinRemember()
  val orientation = LocalConfiguration.current.orientation
  val windowSizeClass = LocalWindowSizeClass.current

  var currentMainUiLayoutMode by remember { mutableStateOf(MainUiLayoutMode.Phone) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      combine(
        flow = appSettings.layoutType.listen(),
        flow2 = snapshotFlow { orientation },
        flow3 = snapshotFlow { globalUiInfoManager.totalScreenWidthState.value },
        flow4 = snapshotFlow { windowSizeClass },
        transform = { t1, t2, t3, t4 -> Tuple4(t1, t2, t3, t4) }
      )
        .collectLatest { (layoutType, orientation, totalScreenWidth, windowSizeClass) ->
          currentMainUiLayoutMode = globalUiInfoManager.updateLayoutModeAndCurrentPage(
            layoutType = layoutType,
            orientation = orientation,
            totalScreenWidth = totalScreenWidth,
            windowSizeClass = windowSizeClass
          )
        }
    }
  )

  CompositionLocalProvider(LocalMainUiLayoutMode provides currentMainUiLayoutMode) {
    content()
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContentInternal(
  screenKey: ScreenKey,
  homeScreenProvider: () -> HomeScreen,
  navigationRouterProvider: () -> NavigationRouter,
  handleBackPresses: @Composable () -> Unit
) {
  val insets = LocalWindowInsets.current
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()

  val contentPadding = remember(
    key1 = insets.left,
    key2 = insets.right
  ) { PaddingValues(start = insets.left, end = insets.right) }

  val kurobaSnackbarState = rememberKurobaSnackbarState()

  handleBackPresses()

  GradientBackground(
    modifier = Modifier
      .fillMaxSize()
      .padding(contentPadding)
      .semantics { testTagsAsResourceId = true },
  ) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize()
    ) {
      val availableWidth = constraints.maxWidth
      val availableHeight = constraints.maxHeight

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