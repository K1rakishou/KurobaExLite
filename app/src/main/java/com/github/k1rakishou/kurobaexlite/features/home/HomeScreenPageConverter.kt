package com.github.k1rakishou.kurobaexlite.features.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.features.home.pages.AbstractPage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SinglePage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SplitPage
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

class HomeScreenPageConverter(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {
  private val catalogScreen by lazy {
    ComposeScreen.createScreen<CatalogScreen>(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter.childRouter(CatalogScreen.SCREEN_KEY)
    )
  }

  private val threadScreen by lazy {
    ComposeScreen.createScreen<ThreadScreen>(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter.childRouter(ThreadScreen.SCREEN_KEY)
    )
  }

  fun convertScreensToPages(
    uiLayoutMode: MainUiLayoutMode,
  ): PagesWrapper {
    val pages = mutableListOf<AbstractPage<ComposeScreenWithToolbar<*>>>()

    when (uiLayoutMode) {
      MainUiLayoutMode.Phone -> {
        pages += SinglePage.of(catalogScreen)
        pages += SinglePage.of(threadScreen)
      }
      MainUiLayoutMode.Split -> {
        pages += SplitPage.of(
          Pair(catalogScreen, GlobalUiInfoManager.CATALOG_SCREEN_WEIGHT),
          Pair(threadScreen, GlobalUiInfoManager.THREAD_SCREEN_WEIGHT)
        )
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
    val pages: List<AbstractPage<ComposeScreenWithToolbar<*>>>
  ) {

    val pagesCount: Int
      get() = pages.size

    fun isEmpty(): Boolean = pages.isEmpty()

    fun pageByIndex(index: Int): AbstractPage<ComposeScreenWithToolbar<*>>? {
      if (pages.isEmpty()) {
        return null
      }

      var foundScreenKey: ScreenKey? = null
      val correctedIndex = index.coerceIn(0, pages.lastIndex)

      iterateScreens { screenIndex, screen ->
        if (screenIndex == correctedIndex) {
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

    private fun iterateScreens(iterator: (Int, ComposeScreenWithToolbar<*>) -> Unit) {
      var index = 0

      for (page in pages) {
        when (page) {
          is SinglePage -> {
            iterator(index, page.screen.composeScreen)
            ++index
          }
          is SplitPage -> {
            iterator(index, page.childScreens[0].composeScreen)
            iterator(index, page.childScreens[1].composeScreen)
            ++index
          }
        }
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as PagesWrapper

      if (pages.size != other.pages.size) return false

      for (index in pages.indices) {
        val thisPage = pages[index]
        val otherPage = other.pages[index]

        if (thisPage != otherPage) {
          return false
        }
      }

      return true
    }

    override fun hashCode(): Int {
      return pages.hashCode()
    }

  }

}