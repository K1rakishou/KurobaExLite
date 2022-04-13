package com.github.k1rakishou.kurobaexlite.features.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.features.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.features.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.features.helpers.layout.ScreenLayout
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

class HomeChildScreens(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {
  private val homeScreenLayouter by lazy { HomeScreenLayouter(componentActivity, navigationRouter) }

  fun getChildScreens(
    uiLayoutMode: MainUiLayoutMode,
    bookmarksScreenOnLeftSide: Boolean
  ): ChildScreens {
    val screens = homeScreenLayouter.layoutScreens(uiLayoutMode, bookmarksScreenOnLeftSide)
    return ChildScreens(screens)
  }

  fun isMainScreen(currentPage: CurrentPage): Boolean {
    return mainScreenKey() == currentPage.screenKey
  }

  fun mainScreenKey(): ScreenKey {
    return CatalogScreen.SCREEN_KEY
  }

  fun screenIndexByPage(currentPage: CurrentPage, childScreens: ChildScreens): Int? {
    return childScreens.screens
      .indexOfFirst { screen ->
        if (screen is ScreenLayout<*>) {
          if (screen.screenKey == currentPage.screenKey) {
            return@indexOfFirst true
          }

          return@indexOfFirst screen.hasScreen(currentPage.screenKey )
        } else {
          return@indexOfFirst screen.screenKey == currentPage.screenKey
        }
      }
      .takeIf { screenIndex -> screenIndex >= 0 }
  }

  @Stable
  data class ChildScreens(val screens: List<ComposeScreenWithToolbar>)

}