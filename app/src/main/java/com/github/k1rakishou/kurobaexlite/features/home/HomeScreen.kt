package com.github.k1rakishou.kurobaexlite.features.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.features.drawer.HomeScreenDrawerLayout
import com.github.k1rakishou.kurobaexlite.features.drawer.detectDrawerDragGestures
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.ScreenLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val homeChildScreens by lazy { HomeChildScreens(componentActivity, navigationRouter) }

  private val currentChildScreensState = mutableStateOf<HomeChildScreens.ChildScreens?>(null)
  private val currentChildIndex = mutableStateOf<Int?>(null)

  override val screenKey: ScreenKey = SCREEN_KEY

  @Suppress("UnnecessaryVariable")
  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current

    val orientationMut by uiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    var uiInfoManagerInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        uiInfoManager.init()
        uiInfoManagerInitialized = true
      })

    if (!uiInfoManagerInitialized) {
      return
    }

    val mainUiLayoutModeMut by uiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut
    if (mainUiLayoutMode == null) {
      return
    }

    val bookmarksScreenOnLeftSide by uiInfoManager.bookmarksScreenOnLeftSide.collectAsState()
    val currentPage by uiInfoManager.currentPageFlow(mainUiLayoutMode).collectAsState()

    val childScreens = remember(
      key1 = bookmarksScreenOnLeftSide,
      key2 = mainUiLayoutMode
    ) {
      val childScreens = homeChildScreens.getChildScreens(mainUiLayoutMode, bookmarksScreenOnLeftSide)
      currentChildScreensState.value = childScreens

      return@remember childScreens
    }

    val initialScreenIndexMut = remember(key1 = currentPage, key2 = childScreens) {
      val initialChildIndex = homeChildScreens.screenIndexByPage(currentPage, childScreens)
      currentChildIndex.value = initialChildIndex

      return@remember initialChildIndex
    }

    val initialScreenIndex = initialScreenIndexMut
    if (initialScreenIndex == null) {
      return
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
    val pagerState = rememberPagerState(key1 = orientation, initialPage = initialScreenIndex)

    LaunchedEffect(
      key1 = mainUiLayoutMode,
      key2 = bookmarksScreenOnLeftSide,
      block = {
        uiInfoManager.currentPageFlow(mainUiLayoutMode).collect { currentPage ->
          scrollToPageByScreenKey(
            screenKey = currentPage.screenKey,
            childScreens = childScreens,
            pagerState = pagerState,
            animate = currentPage.animate
          )
        }
      })

    LaunchedEffect(
      key1 = pagerState.currentPage,
      key2 = mainUiLayoutMode,
      block = {
        val screenKey = childScreens.screens.getOrNull(pagerState.currentPage)?.screenKey
          ?: return@LaunchedEffect

        uiInfoManager.updateCurrentPageForLayoutMode(
          screenKey = screenKey,
          mainUiLayoutMode = mainUiLayoutMode
        )
      }
    )

    val childScreensUpdated by rememberUpdatedState(newValue = childScreens)

    HandleBackPresses {
      if (uiInfoManager.isDrawerOpenedOrOpening()) {
        uiInfoManager.closeDrawer()
        return@HandleBackPresses true
      }

      val freshCurrentPage = uiInfoManager.currentPage(mainUiLayoutMode)
        ?: return@HandleBackPresses false

      // First, process all child screens
      val currentScreenIndex = homeChildScreens.screenIndexByPage(freshCurrentPage, childScreensUpdated)
      if (currentScreenIndex != null) {
        val screens = childScreensUpdated.screens
        val currentScreen = screens.get(currentScreenIndex)

        if (currentScreen is ScreenLayout<*>) {
          for (childScreen in currentScreen.childScreens.asReversed()) {
            if (childScreen.composeScreen.onBackPressed()) {
              return@HandleBackPresses true
            }
          }
        } else {
          if (currentScreen.onBackPressed()) {
            return@HandleBackPresses true
          }
        }
      }

      // Then reset the ViewPager's current page
      if (!homeChildScreens.isMainScreen(freshCurrentPage)) {
        uiInfoManager.updateCurrentPage(homeChildScreens.mainScreenKey())
        return@HandleBackPresses true
      }

      return@HandleBackPresses false
    }

    HomeScreenContentActual(
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

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun HomeScreenContentActual(
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
          uiInfoManager.dragDrawer(dragging, progress, velocity)
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
              isDrawerOpened = { uiInfoManager.isDrawerFullyOpened() },
              onStopConsumingScrollEvents = { consumeAllScrollEvents = false },
              onDraggingDrawer = { dragging, progress, velocity ->
                uiInfoManager.dragDrawer(dragging, progress, velocity)
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
        val childScreen = childScreens.screens[page]
        val transitionIsProgress = pagerState.currentPage != pagerState.targetPage

        Box(
          modifier = Modifier
            .fillMaxSize()
            .consumeClicks(enabled = transitionIsProgress)
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
      )

      HomeScreenFloatingActionButton(
        insets = insets,
        pagerState = pagerState,
        childScreens = childScreens.screens,
        mainUiLayoutMode = mainUiLayoutMode,
        homeScreenViewModel = homeScreenViewModel
      )

      if (drawerWidth > 0) {
        HomeScreenDrawerLayout(
          drawerWidth = drawerWidth,
          navigationRouter = navigationRouter,
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