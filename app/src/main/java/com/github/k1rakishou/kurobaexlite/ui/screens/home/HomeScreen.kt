package com.github.k1rakishou.kurobaexlite.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
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
import com.github.k1rakishou.kurobaexlite.ui.screens.drawer.HomeScreenDrawerLayout
import com.github.k1rakishou.kurobaexlite.ui.screens.drawer.detectDrawerDragGestures
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

val LocalMainUiLayoutMode = staticCompositionLocalOf<MainUiLayoutMode> { error("MainUiLayoutMode not provided") }

class HomeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val appSettings by inject<AppSettings>(AppSettings::class.java)
  private val homeChildScreens by lazy { HomeChildScreens(componentActivity, navigationRouter) }

  override val screenKey: ScreenKey = SCREEN_KEY

  @Suppress("UnnecessaryVariable")
  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val layoutTypeState by appSettings.layoutType
      .listenAsStateFlow(coroutineScope).collectAsState()
    val bookmarksScreenOnLeftSideState by appSettings.bookmarksScreenOnLeftSide
      .listenAsStateFlow(coroutineScope).collectAsState()

    val layoutType = layoutTypeState
    val bookmarksScreenOnLeftSide = bookmarksScreenOnLeftSideState

    if (layoutType == null || bookmarksScreenOnLeftSide == null) {
      return
    }

    val mainUiLayoutMode = remember(key1 = layoutType, key2 = configuration) {
      homeChildScreens.layoutTypeToMainUiLayoutMode(layoutType, configuration)
    }

    val childScreens = remember(layoutType, configuration.orientation, bookmarksScreenOnLeftSide) {
      homeChildScreens.getChildScreens(layoutType, configuration, bookmarksScreenOnLeftSide)
    }
    val initialScreenIndex = remember(layoutType, configuration.orientation, childScreens) {
      homeChildScreens.getInitialScreenIndex(layoutType, configuration, childScreens)
    }

    val drawerLongtapGestureZonePx = with(LocalDensity.current) { remember { 24.dp.toPx() } }
    val maxDrawerWidth = with(LocalDensity.current) { remember { 600.dp.toPx().toInt() } }
    val drawerPhoneVisibleWindowWidth = with(LocalDensity.current) { remember { 40.dp.toPx().toInt() } }

    // rememberSaveable currently does not recreate objects when it's keys change, instead it uses
    // the restored object from the saver. This causes crashes when going from portrait to landscape
    // when we are currently on a thread viewpager page since the pagerState.currentPage ends up
    // being 2 while there are only 2 screens in landscape mode.
    // There is an issue to add support for that on the google's issues tracker but it's almost
    // 2 years old. So for the time being we have to hack around the issue.
    val pagerState = rememberPagerState(key1 = configuration.orientation, initialPage = initialScreenIndex)

    LaunchedEffect(
      layoutType,
      configuration.orientation,
      bookmarksScreenOnLeftSide,
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

    navigationRouter.HandleBackPresses(
      screenKey = screenKey,
      onBackPressed = {
        val currentPage = homeScreenViewModel.currentPage

        if (currentPage != null && !homeChildScreens.isMainScreen(configuration, currentPage)) {
          homeScreenViewModel.updateCurrentPage(
            screenKey = homeChildScreens.mainScreenKey(configuration)
          )

          return@HandleBackPresses true
        }

        return@HandleBackPresses false
      }
    )

    CompositionLocalProvider(LocalMainUiLayoutMode provides mainUiLayoutMode) {
      ActualContent(
        maxDrawerWidth = maxDrawerWidth,
        mainUiLayoutMode = mainUiLayoutMode,
        drawerPhoneVisibleWindowWidth = drawerPhoneVisibleWindowWidth,
        drawerLongtapGestureZonePx = drawerLongtapGestureZonePx,
        pagerState = pagerState,
        childScreens = childScreens,
        insets = insets,
        chanTheme = chanTheme
      )
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun ActualContent(
    maxDrawerWidth: Int,
    mainUiLayoutMode: MainUiLayoutMode,
    drawerPhoneVisibleWindowWidth: Int,
    drawerLongtapGestureZonePx: Float,
    pagerState: PagerState,
    childScreens: HomeChildScreens.ChildScreens,
    insets: Insets,
    chanTheme: ChanTheme
  ) {
    var drawerWidth by remember { mutableStateOf(0) }
    var homeScreenSize by remember { mutableStateOf(IntSize.Zero) }
    var consumeAllScrollEvents by remember { mutableStateOf(false) }

    // TODO(KurobaEx): right now this is hardcoded and I need to implement an interface that will
    //  allow the users building zones like this one.
    val pagerSwipeExclusionZone = remember(key1 = homeScreenSize) {
      if (homeScreenSize.width > 0 && homeScreenSize.height > 0) {
        Rect(Offset(0f, homeScreenSize.height - 600f), Size(350f, 600f))
      } else {
        Rect.Zero
      }
    }

    val nestedScrollConnection = remember(key1 = drawerWidth) {
      HomePagerNestedScrollConnection(
        drawerWidth = drawerWidth.toFloat(),
        currentPagerPage = { pagerState.currentPage },
        shouldConsumeAllScrollEvents = { consumeAllScrollEvents },
        onDragging = { dragging, progress, velocity ->
          homeScreenViewModel.dragDrawer(dragging, progress, velocity)
        })
    }

    Box(
      modifier = Modifier
        .onSizeChanged { size ->
          homeScreenSize = size
          drawerWidth = size.width.coerceAtMost(maxDrawerWidth) - drawerPhoneVisibleWindowWidth
        }
        .pointerInput(
          drawerLongtapGestureZonePx,
          drawerPhoneVisibleWindowWidth,
          drawerWidth,
          pagerSwipeExclusionZone,
          block = {
            detectDrawerDragGestures(
              drawerLongtapGestureZonePx = drawerLongtapGestureZonePx,
              drawerPhoneVisibleWindowWidthPx = drawerPhoneVisibleWindowWidth.toFloat(),
              drawerWidth = drawerWidth.toFloat(),
              pagerSwipeExclusionZone = pagerSwipeExclusionZone,
              isDrawerOpened = { homeScreenViewModel.isDrawerFullyOpened() },
              onStopConsumingScrollEvents = { consumeAllScrollEvents = false },
              onDraggingDrawer = { dragging, progress, velocity ->
                homeScreenViewModel.dragDrawer(dragging, progress, velocity)
              }
            )
          }
        )
        .nestedScroll(nestedScrollConnection)
        .drawDebugPagerSwipeExclusionZone(pagerSwipeExclusionZone)
    ) {
      HorizontalPager(
        modifier = Modifier.fillMaxSize(),
        state = pagerState,
        count = childScreens.screens.size
      ) { page ->
        LaunchedEffect(
          key1 = pagerState.currentPage,
          block = {
            val screenKey = childScreens.screens.getOrNull(pagerState.currentPage)?.screenKey
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

        val childScreen = childScreens.screens[page]
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
        childScreens = childScreens.screens,
        mainUiLayoutMode = mainUiLayoutMode,
        homeScreenViewModel = homeScreenViewModel
      )

      if (drawerWidth > 0) {
        HomeScreenDrawerLayout(
          screenKey = screenKey,
          drawerWidth = drawerWidth,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          homeScreenViewModel = homeScreenViewModel
        )
      }
    }
  }

  private fun Modifier.drawDebugPagerSwipeExclusionZone(pagerSwipeExclusionZone: Rect): Modifier {
    if (!DRAW_PAGER_SWIPE_EXCLUSION_ZONE) {
      return this
    }

    return drawWithContent {
      drawContent()

      if (!pagerSwipeExclusionZone.size.isEmpty()) {
        drawRect(
          color = Color.Green,
          topLeft = pagerSwipeExclusionZone.topLeft,
          size = pagerSwipeExclusionZone.size,
          alpha = 0.5f
        )
      }
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  private suspend fun scrollToPageByScreenKey(
    screenKey: ScreenKey,
    childScreens: HomeChildScreens.ChildScreens,
    pagerState: PagerState,
    animate: Boolean
  ) {
    val indexOfPage = childScreens.screens
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

    private const val DRAW_PAGER_SWIPE_EXCLUSION_ZONE = false
  }
}