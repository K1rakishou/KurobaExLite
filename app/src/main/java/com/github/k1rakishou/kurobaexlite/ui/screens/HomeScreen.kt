package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeChildScreens
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeScreenToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
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
    val configuration = LocalConfiguration.current
    val childScreens = homeChildScreens.getChildScreens(configuration)
    val initialScreenIndex = homeChildScreens.getInitialScreenIndex(configuration, childScreens)

    // rememberSaveable currently does not recreate objects when it's keys change, instead it uses
    // the restored object from the saver. This causes crashes when going from portrait to landscape
    // when we are currently on a thread viewpager page since the pagerState.currentPage ends up
    // being 2 while there are only 2 screens in landscape mode.
    // There is an issue to add support for that on the google's issues tracker but it's almost
    // 2 years old. So for the time being we have to hack around the issue.
    val pagerState = rememberPagerState(key1 = configuration.orientation, initialPage = initialScreenIndex)

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

    homeChildScreens.HandleBackPresses()

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      state = pagerState,
      count = childScreens.size
    ) { page ->
      LaunchedEffect(
        key1 = pagerState.currentPage,
        block = {
          val screenKey = childScreens.getOrNull(pagerState.currentPage)?.screenKey
            ?: return@LaunchedEffect

          // When we manually scroll the pager we need to keep track of the current page,
          // however we don't want to notify the listeners in this case.
          homeScreenViewModel.updateCurrentPage(
            screenKey = screenKey,
            animate = false,
            notifyListeners = false
          )
        }
      )

      val childScreen = childScreens[page]
      val transitionIsProgress = pagerState.currentPage != pagerState.targetPage

      Box(
        modifier = Modifier
          .fillMaxSize()
          .consumeClicks(consume = transitionIsProgress)
      ) {
        childScreen.Content()
      }
    }

    HomeScreenToolbarContainer(
      insets = insets,
      chanTheme = chanTheme,
      pagerState = pagerState,
      childScreens = childScreens,
      homeScreenViewModel = homeScreenViewModel
    )
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