package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

  fun getChildScreens(): List<ComposeScreenWithToolbar> {
    return when (uiInfoManager.mainUiLayoutMode()) {
      MainUiLayoutMode.Portrait -> portraitScreens
      MainUiLayoutMode.TwoWaySplit -> twoWaySplitScreens
    }
  }

  fun getInitialScreenIndex(childScreens: List<ComposeScreen>): Int {
    return when (uiInfoManager.mainUiLayoutMode()) {
      MainUiLayoutMode.Portrait -> {
        childScreens
          .indexOfFirst { it.screenKey == CatalogScreen.SCREEN_KEY }
      }
      MainUiLayoutMode.TwoWaySplit -> {
        childScreens
          .indexOfFirst { it.screenKey == SplitScreenLayout.SCREEN_KEY }
      }
    }
  }

  @Composable
  fun HandleBackPresses() {
    DisposableEffect(key1 = Unit, effect = {
      val handler = object : NavigationRouter.OnBackPressHandler {
        override fun onBackPressed(): Boolean {
          val currentPage = homeScreenViewModel.currentPage

          if (currentPage != null && !isMainScreen(currentPage)) {
            homeScreenViewModel.updateCurrentPage(screenKey = mainScreenKey())
            return true
          }

          return false
        }
      }

      navigationRouter.addOnBackPressedHandler(handler)

      onDispose { navigationRouter.removeOnBackPressedHandler(handler) }
    })
  }

  fun isMainScreen(currentPage: HomeScreenViewModel.CurrentPage): Boolean {
    return mainScreenKey() == currentPage.screenKey
  }

  fun mainScreenKey(): ScreenKey {
    if (uiInfoManager.mainUiLayoutMode().isSplit) {
      return SplitScreenLayout.SCREEN_KEY
    }

    return CatalogScreen.SCREEN_KEY
  }

}