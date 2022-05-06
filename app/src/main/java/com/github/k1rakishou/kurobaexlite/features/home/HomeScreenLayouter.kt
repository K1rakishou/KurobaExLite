package com.github.k1rakishou.kurobaexlite.features.home

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.features.navigation.HistoryScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.ScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SplitScreenLayout

class HomeScreenLayouter(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {

  private val portraitScreens by lazy {
    return@lazy listOf<ComposeScreenWithToolbar>(
      HistoryScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(HistoryScreen.SCREEN_KEY)
      ),
      CatalogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(CatalogScreen.SCREEN_KEY)
      ),
      ThreadScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(ThreadScreen.SCREEN_KEY)
      )
    )
  }

  private val splitScreens by lazy {
    return@lazy listOf<ComposeScreenWithToolbar>(
      HistoryScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(HistoryScreen.SCREEN_KEY)
      ),
      SplitScreenLayout(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(SplitScreenLayout.SCREEN_KEY),
        childScreensBuilder = { router ->
          return@SplitScreenLayout listOf(
            ScreenLayout.ChildScreen(
              composeScreen = CatalogScreen(
                componentActivity = componentActivity,
                navigationRouter = router.childRouter(CatalogScreen.SCREEN_KEY)
              ),
              weight = GlobalUiInfoManager.CATALOG_SCREEN_WEIGHT
            ),
            ScreenLayout.ChildScreen(
              composeScreen = ThreadScreen(
                componentActivity = componentActivity,
                navigationRouter = router.childRouter(ThreadScreen.SCREEN_KEY)
              ),
              weight = GlobalUiInfoManager.THREAD_SCREEN_WEIGHT
            )
          )
        }
      )
    )
  }

  fun layoutScreens(
    uiLayoutMode: MainUiLayoutMode,
    historyScreenOnLeftSide: Boolean
  ): List<ComposeScreenWithToolbar> {
    when (uiLayoutMode) {
      MainUiLayoutMode.Phone -> {
        if (historyScreenOnLeftSide) {
          return portraitScreens
        }

        val screens = portraitScreens.toMutableList()
        screens.add(screens.removeAt(0))

        return screens
      }
      MainUiLayoutMode.Split -> {
        if (historyScreenOnLeftSide) {
          return splitScreens
        }

        val screens = splitScreens.toMutableList()
        screens.add(screens.removeAt(0))

        return screens
      }
    }
  }

}