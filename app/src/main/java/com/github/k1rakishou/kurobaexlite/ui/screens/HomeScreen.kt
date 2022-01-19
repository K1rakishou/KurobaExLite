package com.github.k1rakishou.kurobaexlite.ui.screens

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks.BookmarksScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.SplitScreenLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreen(
  componentActivity: ComponentActivity
) : ComposeScreen(componentActivity) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()

  private val portraitChildScreens = listOf<ComposeScreen>(
    BookmarksScreen(componentActivity),
    CatalogScreen(componentActivity),
    ThreadScreen(componentActivity)
  )

  private val albumChildScreens = listOf<ComposeScreen>(
    BookmarksScreen(componentActivity),
    SplitScreenLayout(
      componentActivity = componentActivity,
      orientation = SplitScreenLayout.Orientation.Horizontal,
      childScreens = listOf(
        SplitScreenLayout.ChildScreen(CatalogScreen(componentActivity), 0.4f),
        SplitScreenLayout.ChildScreen(ThreadScreen(componentActivity), 0.6f)
      )
    )
  )

  override val screenKey: ScreenKey = SCREEN_KEY

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val childScreens = with(LocalConfiguration.current) {
      remember(key1 = orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          portraitChildScreens
        } else {
          albumChildScreens
        }
      }
    }

    val pagerState = rememberPagerState()

    LaunchedEffect(
      key1 = Unit,
      block = {
        homeScreenViewModel.updateCurrentPage(
          screenKey = CatalogScreen.SCREEN_KEY,
          animate = false
        )

        homeScreenViewModel.currentPage.collect { currentPage ->
          scrollToPageByScreenKey(
            screenKey = currentPage.screenKey,
            childScreens = childScreens,
            pagerState = pagerState,
            animate = currentPage.animate
          )
        }
      })

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      state = pagerState,
      count = childScreens.size
    ) { page ->
      childScreens[page].Content()
    }
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