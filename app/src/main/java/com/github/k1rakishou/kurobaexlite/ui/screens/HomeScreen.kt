package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeChildScreens
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val homeChildScreens by lazy { HomeChildScreens(componentActivity, navigationRouter) }

  override val screenKey: ScreenKey = SCREEN_KEY

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val childScreens = homeChildScreens.getChildScreens()
    val initialScreenIndex = homeChildScreens.getInitialScreenIndex(childScreens = childScreens)
    val pagerState = rememberPagerState(initialPage = initialScreenIndex)

    SetDefaultScreen(childScreens, pagerState)
    homeChildScreens.HandleBackPresses()

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      state = pagerState,
      count = childScreens.size
    ) { page ->
      LaunchedEffect(
        key1 = currentPage,
        block = {
          val screenKey = childScreens[currentPage].screenKey

          // When we manually scroll the pager we need to keep track of the current page
          homeScreenViewModel.updateCurrentPage(
            screenKey = screenKey,
            animate = false,
            notifyListeners = false
          )
        }
      )

      val childScreen = childScreens[page]

      Box(modifier = Modifier.fillMaxSize()) {
        childScreen.Content()
        ScreenToolbarContainer(insets, chanTheme, childScreen)
      }
    }
  }

  @Composable
  private fun ScreenToolbarContainer(
    insets: Insets,
    chanTheme: ChanTheme,
    childScreen: ComposeScreenWithToolbar
  ) {
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val toolbarTotalHeight = remember(key1 = insets.topDp) { insets.topDp + toolbarHeight }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarTotalHeight)
        .background(chanTheme.primaryColorCompose)
    ) {
      Spacer(modifier = Modifier.height(insets.topDp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(toolbarHeight)
      ) {
        childScreen.Toolbar(this)
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