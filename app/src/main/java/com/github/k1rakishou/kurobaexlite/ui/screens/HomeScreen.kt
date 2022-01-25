package com.github.k1rakishou.kurobaexlite.ui.screens

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks.BookmarksScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.SplitScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()

  private val portraitChildScreens by lazy {
    return@lazy listOf<ComposeScreen>(
      BookmarksScreen(componentActivity, navigationRouter.childRouter(BookmarksScreen.SCREEN_KEY.key)),
      CatalogScreen(componentActivity, navigationRouter.childRouter(CatalogScreen.SCREEN_KEY.key)),
      ThreadScreen(componentActivity, navigationRouter.childRouter(ThreadScreen.SCREEN_KEY.key))
    )
  }

  private val albumChildScreens by lazy {
    return@lazy listOf<ComposeScreen>(
      BookmarksScreen(componentActivity, navigationRouter.childRouter(BookmarksScreen.SCREEN_KEY.key)),
      SplitScreenLayout(
        componentActivity = componentActivity,
        navigationRouter= navigationRouter.childRouter(SplitScreenLayout.SCREEN_KEY.key),
        orientation = SplitScreenLayout.Orientation.Horizontal,
        childScreensBuilder = { router ->
          return@SplitScreenLayout listOf(
            SplitScreenLayout.ChildScreen(
              CatalogScreen(componentActivity, router.childRouter(CatalogScreen.SCREEN_KEY.key)),
              0.4f
            ),
            SplitScreenLayout.ChildScreen(
              ThreadScreen(componentActivity, router.childRouter(ThreadScreen.SCREEN_KEY.key)),
              0.6f
            )
          )
        }
      )
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val childScreens = getChildScreens()
    val pagerState = rememberPagerState()

    SetDefaultScreen(childScreens, pagerState)
    HandleBackPresses()

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      state = pagerState,
      count = childScreens.size
    ) { page ->
      LaunchedEffect(
        key1 = currentPage,
        block = {
          val screenKey = childScreens[currentPage].screenKey
          homeScreenViewModel.updateCurrentPage(screenKey, false)
        }
      )

      childScreens[page].Content()
    }
  }

  @Composable
  private fun HandleBackPresses() {
    DisposableEffect(key1 = Unit, effect = {
      val handler = object : NavigationRouter.OnBackPressHandler {
        override fun onBackPressed(): Boolean {
          val currentPage = homeScreenViewModel.currentPage

          if (currentPage != null && currentPage.screenKey != CatalogScreen.SCREEN_KEY) {
            homeScreenViewModel.updateCurrentPage(
              screenKey = CatalogScreen.SCREEN_KEY,
              animate = true
            )
            return true
          }

          return false
        }
      }

      navigationRouter.addOnBackPressedHandler(handler)

      onDispose { navigationRouter.removeOnBackPressedHandler(handler) }
    })
  }

  @Composable
  private fun getChildScreens(): List<ComposeScreen> {
    return with(LocalConfiguration.current) {
      remember(key1 = orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          portraitChildScreens
        } else {
          albumChildScreens
        }
      }
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun HomeScreen.SetDefaultScreen(
    childScreens: List<ComposeScreen>,
    pagerState: PagerState
  ) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        homeScreenViewModel.updateCurrentPage(
          screenKey = CatalogScreen.SCREEN_KEY,
          animate = false
        )

        homeScreenViewModel.currentPageFlow.collect { currentPage ->
          scrollToPageByScreenKey(
            screenKey = currentPage.screenKey,
            childScreens = childScreens,
            pagerState = pagerState,
            animate = currentPage.animate
          )
        }
      })
  }

  @OptIn(ExperimentalPagerApi::class)
  private suspend fun scrollToPageByScreenKey(
    screenKey: ScreenKey,
    childScreens: List<ComposeScreen>,
    pagerState: PagerState,
    animate: Boolean
  ) {
    val indexOfPage = childScreens
      .indexOfFirst { it.screenKey == screenKey }

    if (indexOfPage >= 0) {
      if (animate) {
        pagerState.animateScrollToPage(page = indexOfPage)
      } else {
        pagerState.scrollToPage(page = indexOfPage)
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("HomeScreen")
  }
}