package com.github.k1rakishou.kurobaexlite.ui.screens.home

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.layout.SplitScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import org.koin.java.KoinJavaComponent.inject

class HomeChildScreens(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {
  private val uiInfoManager by inject<UiInfoManager>(UiInfoManager::class.java)
  private val homeScreenLayouter by lazy { HomeScreenLayouter(componentActivity, navigationRouter, uiInfoManager) }

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
    if (uiInfoManager.currentUiLayoutModeState.value == MainUiLayoutMode.Split) {
      return SplitScreenLayout.SCREEN_KEY
    }

    return CatalogScreen.SCREEN_KEY
  }

  data class ChildScreens(val screens: List<ComposeScreenWithToolbar>)

}