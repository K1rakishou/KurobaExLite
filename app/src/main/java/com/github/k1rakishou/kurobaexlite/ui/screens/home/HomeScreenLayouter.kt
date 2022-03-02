package com.github.k1rakishou.kurobaexlite.ui.screens.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks.BookmarksScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.layout.SplitScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen

class HomeScreenLayouter(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val uiInfoManager: UiInfoManager
) {

  private val portraitScreens by lazy {
    return@lazy listOf<ComposeScreenWithToolbar>(
      BookmarksScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(BookmarksScreen.SCREEN_KEY.key)
      ),
      CatalogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(CatalogScreen.SCREEN_KEY.key)
      ),
      ThreadScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(ThreadScreen.SCREEN_KEY.key)
      )
    )
  }

  private val splitScreens by lazy {
    return@lazy listOf<ComposeScreenWithToolbar>(
      BookmarksScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter.childRouter(BookmarksScreen.SCREEN_KEY.key)
      ),
      SplitScreenLayout(
        componentActivity = componentActivity,
        navigationRouter= navigationRouter.childRouter(SplitScreenLayout.SCREEN_KEY.key),
        childScreensBuilder = { router ->
          return@SplitScreenLayout listOf(
            SplitScreenLayout.ChildScreen(
              composeScreen = CatalogScreen(
                componentActivity = componentActivity,
                navigationRouter = router.childRouter(CatalogScreen.SCREEN_KEY.key)
              ),
              weight = 0.4f
            ),
            SplitScreenLayout.ChildScreen(
              composeScreen = ThreadScreen(
                componentActivity = componentActivity,
                navigationRouter = router.childRouter(ThreadScreen.SCREEN_KEY.key)
              ),
              weight = 0.6f
            )
          )
        }
      )
    )
  }

  fun layoutScreens(
    layoutType: LayoutType,
    configuration: Configuration,
    bookmarksScreenOnLeftSide: Boolean
  ): List<ComposeScreenWithToolbar> {
    val mainUiLayoutMode = when (layoutType) {
      LayoutType.Auto -> uiInfoManager.mainUiLayoutMode(configuration = configuration)
      LayoutType.Phone -> MainUiLayoutMode.Portrait
      LayoutType.Split -> MainUiLayoutMode.Split
    }

    when (mainUiLayoutMode) {
      MainUiLayoutMode.Portrait -> {
        if (bookmarksScreenOnLeftSide) {
          return portraitScreens
        }

        val screens = portraitScreens.toMutableList()
        screens.add(screens.removeAt(0))

        return screens
      }
      MainUiLayoutMode.Split -> {
        if (bookmarksScreenOnLeftSide) {
          return splitScreens
        }

        val screens = portraitScreens.toMutableList()
        screens.add(screens.removeAt(0))

        return screens
      }
    }
  }

}