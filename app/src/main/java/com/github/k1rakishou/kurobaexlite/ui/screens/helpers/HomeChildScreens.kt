package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks.BookmarksScreen
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

  private val threeWaySplitScreens by lazy {
    return@lazy listOf<ComposeScreenWithToolbar>(
      SplitScreenLayout(
        componentActivity = componentActivity,
        navigationRouter= navigationRouter.childRouter(SplitScreenLayout.SCREEN_KEY.key),
        isStartScreen = true,
        childScreensBuilder = { router ->
          return@SplitScreenLayout listOf(
            SplitScreenLayout.ChildScreen(
              composeScreen = BookmarksScreen(
                componentActivity = componentActivity,
                navigationRouter = navigationRouter.childRouter(BookmarksScreen.SCREEN_KEY.key),
                isStartScreen = false
              ),
              weight = 0.2f
            ),
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
              weight = 0.4f
            )
          )
        }
      )
    )
  }

  fun getChildScreens(): List<ComposeScreenWithToolbar> {
    return when (currentLayoutMode()) {
      LayoutMode.Portrait -> portraitScreens
      LayoutMode.TwoWaySplit -> twoWaySplitScreens
      LayoutMode.ThreeWaySplit -> threeWaySplitScreens
    }
  }

  fun getInitialScreenIndex(childScreens: List<ComposeScreen>): Int {
    return when (currentLayoutMode()) {
      LayoutMode.Portrait -> {
        childScreens
          .indexOfFirst { it.screenKey == CatalogScreen.SCREEN_KEY }
      }
      LayoutMode.TwoWaySplit,
      LayoutMode.ThreeWaySplit -> {
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
    if (currentLayoutMode().isSplit) {
      return SplitScreenLayout.SCREEN_KEY
    }

    return CatalogScreen.SCREEN_KEY
  }

  private fun currentLayoutMode(): LayoutMode {
    val isTablet = globalConstants.isTablet
    val orientation = componentActivity.resources.configuration.orientation


    if (isTablet) {
      if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        return LayoutMode.TwoWaySplit
      }

      return LayoutMode.ThreeWaySplit
    } else {
      if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        return LayoutMode.Portrait
      }

      return LayoutMode.TwoWaySplit
    }
  }

  enum class LayoutMode(val isSplit: Boolean) {
    Portrait(false),
    TwoWaySplit(true),
    ThreeWaySplit(true)
  }

}