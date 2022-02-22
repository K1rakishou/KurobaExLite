package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks.BookmarksScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.layout.SplitScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class HomeChildScreens(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {
  private val globalConstants by inject<GlobalConstants>(GlobalConstants::class.java)
  private val uiInfoManager by inject<UiInfoManager>(UiInfoManager::class.java)
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()

  private val portraitScreens by lazy {
    return@lazy listOf<ComposeScreenWithToolbar>(
      BookmarksScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(BookmarksScreen.SCREEN_KEY.key),
        isStartScreen = false
      ),
      CatalogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(CatalogScreen.SCREEN_KEY.key),
        isStartScreen = true
      ),
      ThreadScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(ThreadScreen.SCREEN_KEY.key),
        isStartScreen = false
      )
    )
  }

  private val twoWaySplitScreens by lazy {
    return@lazy listOf<ComposeScreenWithToolbar>(
      BookmarksScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(BookmarksScreen.SCREEN_KEY.key),
        isStartScreen = false
      ),
      SplitScreenLayout(
        componentActivity = componentActivity,
        navigationRouter= navigationRouter.childRouter(SplitScreenLayout.SCREEN_KEY.key),
        isStartScreen = true,
        childScreensBuilder = { router ->
          return@SplitScreenLayout listOf(
            SplitScreenLayout.ChildScreen(
              composeScreen = CatalogScreen(
                componentActivity = componentActivity,
                navigationRouter = router.childRouter(CatalogScreen.SCREEN_KEY.key),
                isStartScreen = false
              ),
              weight = 0.4f
            ),
            SplitScreenLayout.ChildScreen(
              composeScreen = ThreadScreen(
                componentActivity = componentActivity,
                navigationRouter = router.childRouter(ThreadScreen.SCREEN_KEY.key),
                isStartScreen = false
              ),
              weight = 0.6f
            )
          )
        }
      )
    )
  }

  fun getChildScreens(configuration: Configuration): List<ComposeScreenWithToolbar> {
    return when (uiInfoManager.mainUiLayoutMode(configuration = configuration)) {
      MainUiLayoutMode.Portrait -> portraitScreens
      MainUiLayoutMode.Split -> twoWaySplitScreens
    }
  }

  fun getInitialScreenIndex(configuration: Configuration, childScreens: List<ComposeScreen>): Int {
    return when (uiInfoManager.mainUiLayoutMode(configuration = configuration)) {
      MainUiLayoutMode.Portrait -> {
        childScreens
          .indexOfFirst { it.screenKey == CatalogScreen.SCREEN_KEY }
      }
      MainUiLayoutMode.Split -> {
        childScreens
          .indexOfFirst { it.screenKey == SplitScreenLayout.SCREEN_KEY }
      }
    }
  }

  @Composable
  fun HandleBackPresses() {
    val configuration = LocalConfiguration.current

    DisposableEffect(key1 = Unit, effect = {
      val handler = object : NavigationRouter.OnBackPressHandler {
        override suspend fun onBackPressed(): Boolean {
          val currentPage = homeScreenViewModel.currentPage

          if (currentPage != null && !isMainScreen(configuration, currentPage)) {
            homeScreenViewModel.updateCurrentPage(screenKey = mainScreenKey(configuration))
            return true
          }

          return false
        }
      }

      navigationRouter.addOnBackPressedHandler(handler)

      onDispose { navigationRouter.removeOnBackPressedHandler(handler) }
    })
  }

  fun isMainScreen(configuration: Configuration, currentPage: HomeScreenViewModel.CurrentPage): Boolean {
    return mainScreenKey(configuration) == currentPage.screenKey
  }

  fun mainScreenKey(configuration: Configuration): ScreenKey {
    if (uiInfoManager.mainUiLayoutMode(configuration) == MainUiLayoutMode.Split) {
      return SplitScreenLayout.SCREEN_KEY
    }

    return CatalogScreen.SCREEN_KEY
  }

}