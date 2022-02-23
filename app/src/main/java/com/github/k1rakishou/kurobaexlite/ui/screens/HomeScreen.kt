package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.screens.drawer.HomeScreenDrawerLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.drawer.detectDrawerDragGestures
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeChildScreens
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeScreenToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class HomeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val appSettings by inject<AppSettings>(AppSettings::class.java)
  private val homeChildScreens by lazy { HomeChildScreens(componentActivity, navigationRouter) }

  override val screenKey: ScreenKey = SCREEN_KEY

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val layoutType by appSettings.layoutType.listenAsStateFlow(coroutineScope).collectAsState()

    val childScreens = remember(layoutType, configuration) {
      homeChildScreens.getChildScreens(layoutType, configuration)
    }
    val initialScreenIndex = remember(layoutType, configuration) {
      homeChildScreens.getInitialScreenIndex(layoutType, configuration, childScreens)
    }

    val drawerLongtapGestureZonePx = with(LocalDensity.current) { remember { 24.dp.toPx() } }
    val maxDrawerWidth = with(LocalDensity.current) { remember { 600.dp.toPx().toInt() } }
    val drawerPhoneVisibleWindowWidth = with(LocalDensity.current) { remember { 40.dp.toPx().toInt() } }
    var drawerWidth by remember { mutableStateOf(0) }

    // rememberSaveable currently does not recreate objects when it's keys change, instead it uses
    // the restored object from the saver. This causes crashes when going from portrait to landscape
    // when we are currently on a thread viewpager page since the pagerState.currentPage ends up
    // being 2 while there are only 2 screens in landscape mode.
    // There is an issue to add support for that on the google's issues tracker but it's almost
    // 2 years old. So for the time being we have to hack around the issue.
    val pagerState = rememberPagerState(key1 = configuration.orientation, initialPage = initialScreenIndex)

    LaunchedEffect(
      key1 = layoutType,
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

    Box(
      modifier = Modifier
        .onSizeChanged { size ->
          drawerWidth = calculateDrawerWidth(size, maxDrawerWidth, drawerPhoneVisibleWindowWidth)
        }
        .pointerInput(
          key1 = Unit,
          block = {
            detectDrawerDragGestures(
              drawerLongtapGestureZonePx = drawerLongtapGestureZonePx,
              drawerPhoneVisibleWindowWidthPx = drawerPhoneVisibleWindowWidth.toFloat(),
              drawerWidth = drawerWidth.toFloat(),
              isDrawerOpened = { homeScreenViewModel.isDrawerOpened() },
              onDraggingDrawer = { dragging, progress, velocity ->
                homeScreenViewModel.dragDrawer(dragging, progress, velocity)
              })
          }
        )
    ) {
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
        val currentPageOffset = Math.abs(pagerState.currentPageOffset)

        val scale = if (transitionIsProgress) {
          if (currentPageOffset <= 0.5f) {
            lerpFloat(transitionScaleMax, transitionScaleMin, currentPageOffset)
          } else {
            lerpFloat(transitionScaleMin, transitionScaleMax, currentPageOffset)
          }
        } else {
          transitionScaleMax
        }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .scale(scale)
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

      if (drawerWidth > 0) {
        HomeScreenDrawerLayout(
          drawerWidth = drawerWidth,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          homeScreenViewModel = homeScreenViewModel
        )
      }
    }
  }

  private fun calculateDrawerWidth(
    size: IntSize,
    maxDrawerWidth: Int,
    drawerPhoneVisibleWindowWidth: Int
  ): Int {
    if (uiInfoManager.isTablet) {
      return size.width.coerceAtMost(maxDrawerWidth)
    }

    return size.width - drawerPhoneVisibleWindowWidth
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

    private const val transitionScaleMax = 1f
    private const val transitionScaleMin = 0.95f
  }
}