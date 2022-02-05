package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeChildScreens
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
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
    val currentPage = pagerState.currentPage
    val targetPage = pagerState.targetPage

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
        key1 = currentPage,
        block = {
          val screenKey = childScreens.getOrNull(currentPage)?.screenKey
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
      val transitionIsProgress = currentPage != targetPage

      Box(
        modifier = Modifier
          .fillMaxSize()
          .consumeClicks(consume = transitionIsProgress)
      ) {
        childScreen.Content()
      }
    }

    ScreenToolbarContainer(
      insets = insets,
      chanTheme = chanTheme,
      pagerState = pagerState,
      currentPage = currentPage,
      targetPage = targetPage,
      childScreens = childScreens
    )
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun ScreenToolbarContainer(
    insets: Insets,
    chanTheme: ChanTheme,
    pagerState: PagerState,
    currentPage: Int,
    targetPage: Int,
    childScreens: List<ComposeScreenWithToolbar>
  ) {
    if (currentPage < 0 || currentPage >= childScreens.size) {
      return
    }
    if (targetPage < 0 || targetPage >= childScreens.size) {
      return
    }

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val toolbarTranslationDistancePx = with(LocalDensity.current) { toolbarHeight.toPx() / 3f }
    val toolbarTotalHeight = remember(key1 = insets.topDp) { insets.topDp + toolbarHeight }

    val animationProgress = pagerState.currentPageOffset
    val transitionIsProgress = currentPage != targetPage

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarTotalHeight)
        .background(chanTheme.primaryColorCompose)
        .consumeClicks()
    ) {
      Spacer(modifier = Modifier.height(insets.topDp))

      Box {
        val currentChildScreen = childScreens[currentPage]
        val currentToolbarAlpha = lerpFloat(1f, 0f, Math.abs(animationProgress))
        val currentToolbarTranslation = if (animationProgress >= 0f) {
          lerpFloat(0f, toolbarTranslationDistancePx, Math.abs(animationProgress))
        } else {
          lerpFloat(0f, -toolbarTranslationDistancePx, Math.abs(animationProgress))
        }

        key(currentChildScreen.screenKey.key) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(toolbarHeight)
              .graphicsLayer {
                alpha = currentToolbarAlpha
                translationY = currentToolbarTranslation
              }
              .consumeClicks(consume = transitionIsProgress)
          ) {
            currentChildScreen.Toolbar(this)
          }
        }

        if (currentPage != targetPage) {
          val targetChildScreen = childScreens[targetPage]
          val targetToolbarAlpha = lerpFloat(0f, 1f, Math.abs(animationProgress))
          val targetToolbarTranslation = if (animationProgress >= 0f) {
            lerpFloat(-toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
          } else {
            lerpFloat(toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
          }

          key(targetChildScreen.screenKey.key) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(toolbarHeight)
                .graphicsLayer {
                  alpha = targetToolbarAlpha
                  translationY = targetToolbarTranslation
                }
                .consumeClicks(consume = transitionIsProgress)
            ) {
              targetChildScreen.Toolbar(this)
            }
          }
        }
      }
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