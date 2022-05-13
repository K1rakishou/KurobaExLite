package com.github.k1rakishou.kurobaexlite.features.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.features.home.pages.AbstractPage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SinglePage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SplitPage
import com.github.k1rakishou.kurobaexlite.features.navigation.HistoryScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

class HomeScreenPageConverter(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {

  private val historyScreen by lazy {
    HistoryScreen(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter.childRouter(HistoryScreen.SCREEN_KEY)
    )
  }

  private val catalogScreen by lazy {
    CatalogScreen(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter.childRouter(CatalogScreen.SCREEN_KEY)
    )
  }

  private val threadScreen by lazy {
    ThreadScreen(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter.childRouter(ThreadScreen.SCREEN_KEY)
    )
  }


  fun convertScreensToPages(
    uiLayoutMode: MainUiLayoutMode,
    historyScreenOnLeftSide: Boolean
  ): PagesWrapper {
    val pages = mutableListOf<AbstractPage<ComposeScreenWithToolbar>>()

    when (uiLayoutMode) {
      MainUiLayoutMode.Phone -> {
        if (historyScreenOnLeftSide) {
          pages += SinglePage.of(historyScreen)
        }

        pages += SinglePage.of(catalogScreen)
        pages += SinglePage.of(threadScreen)

        if (!historyScreenOnLeftSide) {
          pages += SinglePage.of(historyScreen)
        }
      }
      MainUiLayoutMode.Split -> {
        if (historyScreenOnLeftSide) {
          pages += SinglePage.of(historyScreen)
        }

        pages += SplitPage.of(
          Pair(catalogScreen, GlobalUiInfoManager.CATALOG_SCREEN_WEIGHT),
          Pair(threadScreen, GlobalUiInfoManager.THREAD_SCREEN_WEIGHT)
        )

        if (!historyScreenOnLeftSide) {
          pages += SinglePage.of(historyScreen)
        }
      }
    }

    return PagesWrapper(pages)
  }

  fun isMainScreen(screenKey: ScreenKey): Boolean {
    return mainScreenKey() == screenKey
  }

  fun isMainScreen(currentPage: CurrentPage): Boolean {
    return mainScreenKey() == currentPage.screenKey
  }

  fun mainScreenKey(): ScreenKey {
    return CatalogScreen.SCREEN_KEY
  }

  @Stable
  data class PagesWrapper(
    val pages: List<AbstractPage<ComposeScreenWithToolbar>>
  ) {

    val pagesCount: Int
      get() = pages.size

    fun isEmpty(): Boolean = pages.isEmpty()

    fun pageByIndex(index: Int): AbstractPage<ComposeScreenWithToolbar>? {
      var foundScreenKey: ScreenKey? = null

      iterateScreens { screenIndex, screen ->
        if (screenIndex == index) {
          foundScreenKey = screen.screenKey
        }
      }

      val key = foundScreenKey
        ?: return null

      return pages.firstOrNull { it.hasScreen(key) }
    }

    fun screenIndexByScreenKey(screenKey: ScreenKey): Int? {
      return pages
        .indexOfFirst { page -> page.hasScreen(screenKey) }
        .takeIf { screenIndex -> screenIndex >= 0 }
    }

    fun screenKeyByPageIndex(pageIndex: Int): ScreenKey? {
      var foundScreenKey: ScreenKey? = null

      iterateScreens { screenIndex, screen ->
        if (screenIndex == pageIndex) {
          foundScreenKey = screen.screenKey
        }
      }

      return foundScreenKey
    }

    fun iterateScreens(iterator: (Int, ComposeScreenWithToolbar) -> Unit) {
      var index = 0

      for (page in pages) {
        when (page) {
          is SinglePage -> {
            iterator(index++, page.screen.composeScreen)
          }
          is SplitPage -> {
            iterator(index++, page.childScreens[0].composeScreen)
            iterator(index++, page.childScreens[1].composeScreen)
          }
        }
      }
    }

  }

}