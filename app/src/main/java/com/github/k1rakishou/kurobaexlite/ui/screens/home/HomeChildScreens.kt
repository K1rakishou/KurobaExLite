package com.github.k1rakishou.kurobaexlite.ui.screens.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.layout.SplitScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class HomeChildScreens(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {
  private val uiInfoManager by inject<UiInfoManager>(UiInfoManager::class.java)
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val homeScreenLayouter by lazy { HomeScreenLayouter(componentActivity, navigationRouter, uiInfoManager) }

  fun getChildScreens(
    layoutType: LayoutType,
    configuration: Configuration,
    bookmarksScreenOnLeftSide: Boolean
  ): ChildScreens {
    val screens = homeScreenLayouter.layoutScreens(
      layoutType = layoutType,
      configuration = configuration,
      bookmarksScreenOnLeftSide = bookmarksScreenOnLeftSide
    )

    return ChildScreens(screens)
  }

  fun getInitialScreenIndex(
    layoutType: LayoutType,
    configuration: Configuration,
    childScreens: ChildScreens
  ): Int {

    return when (layoutTypeToMainUiLayoutMode(layoutType, configuration)) {
      MainUiLayoutMode.Portrait -> {
        childScreens.screens
          .indexOfFirst { it.screenKey == CatalogScreen.SCREEN_KEY }
      }
      MainUiLayoutMode.Split -> {
        childScreens.screens
          .indexOfFirst { it.screenKey == SplitScreenLayout.SCREEN_KEY }
      }
    }
  }

  fun layoutTypeToMainUiLayoutMode(
    layoutType: LayoutType,
    configuration: Configuration
  ): MainUiLayoutMode {
    return when (layoutType) {
      LayoutType.Auto -> uiInfoManager.mainUiLayoutMode(configuration = configuration)
      LayoutType.Phone -> MainUiLayoutMode.Portrait
      LayoutType.Split -> MainUiLayoutMode.Split
    }
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

  data class ChildScreens(val screens: List<ComposeScreenWithToolbar>)

}