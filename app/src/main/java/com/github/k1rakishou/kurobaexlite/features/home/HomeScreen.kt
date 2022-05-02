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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.drawer.HomeScreenDrawerLayout
import com.github.k1rakishou.kurobaexlite.features.drawer.detectDrawerDragGestures
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.managers.LastVisitedEndpointManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.Insets
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.drawDragLongtapDragGestureZone
import com.github.k1rakishou.kurobaexlite.ui.helpers.drawPagerSwipeExclusionZoneTutorial
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.ScreenLayout
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class HomeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val homeChildScreens by lazy { HomeChildScreens(componentActivity, navigationRouter) }
  private val lastVisitedEndpointManager: LastVisitedEndpointManager by inject(LastVisitedEndpointManager::class.java)

  private val currentChildScreensState = mutableStateOf<HomeChildScreens.ChildScreens?>(null)
  private val currentChildIndex = mutableStateOf<Int?>(null)

  override val screenKey: ScreenKey = SCREEN_KEY

  @Suppress("UnnecessaryVariable")
  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current

    val orientationMut by globalUiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val globalUiInfoViewModelInitialized by globalUiInfoManager.initialized

    LaunchedEffect(
      key1 = Unit,
      block = { globalUiInfoManager.init() }
    )

    if (!globalUiInfoViewModelInitialized) {
      return
    }

    ShowAndProcessSnackbars()

    val mainUiLayoutModeMut by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut
    if (mainUiLayoutMode == null) {
      return
    }

    val bookmarksScreenOnLeftSide by globalUiInfoManager.bookmarksScreenOnLeftSide.collectAsState()
    val currentPage by globalUiInfoManager.currentPageFlow(mainUiLayoutMode).collectAsState()

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

    val drawerLongtapGestureWidthZonePx = with(LocalDensity.current) { remember { 16.dp.toPx() } }
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
        globalUiInfoManager.currentPageFlow(mainUiLayoutMode).collect { currentPage ->
          scrollToPageByScreenKey(
            screenKey = currentPage.screenKey,
            childScreens = childScreens,
            pagerState = pagerState,
            animate = currentPage.animate
          )
        }
      }
    )

    LaunchedEffect(
      key1 = pagerState.currentPage,
      key2 = mainUiLayoutMode,
      block = {
        val screenKey = childScreens.screens.getOrNull(pagerState.currentPage)?.screenKey
          ?: return@LaunchedEffect

        globalUiInfoManager.updateCurrentPageForLayoutMode(
          screenKey = screenKey,
          mainUiLayoutMode = mainUiLayoutMode
        )
      }
    )

    val childScreensUpdated by rememberUpdatedState(newValue = childScreens)

    HandleBackPresses {
      if (globalUiInfoManager.isDrawerOpenedOrOpening()) {
        globalUiInfoManager.closeDrawer()
        return@HandleBackPresses true
      }

      val freshCurrentPage = globalUiInfoManager.currentPage(mainUiLayoutMode)
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
        globalUiInfoManager.updateCurrentPage(homeChildScreens.mainScreenKey())
        return@HandleBackPresses true
      }

      return@HandleBackPresses false
    }

    HomeScreenContentActual(
      maxDrawerWidth = maxDrawerWidth,
      mainUiLayoutMode = mainUiLayoutMode,
      drawerPhoneVisibleWindowWidth = drawerPhoneVisibleWindowWidth,
      drawerLongtapGestureWidthZonePx = drawerLongtapGestureWidthZonePx,
      pagerState = pagerState,
      childScreens = childScreens,
      insets = insets,
      chanTheme = chanTheme
    )
  }

  @Composable
  private fun ShowAndProcessSnackbars() {
    val context = LocalContext.current

    LaunchedEffect(
      key1 = Unit,
      block = {
        snackbarManager.removedSnackbarsFlow.collect { removedSnackbarInfoSet ->
          val removedSnackbarIds = removedSnackbarInfoSet.map { it.snackbarId }.toSet()

          if (SnackbarId.ReloadLastVisitedCatalog in removedSnackbarIds) {
            appSettings.lastVisitedCatalog.remove()
          }
          if (SnackbarId.ReloadLastVisitedThread in removedSnackbarIds) {
            appSettings.lastVisitedThread.remove()
          }
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
          if (snackbarClickable.key !is SnackbarButton) {
            return@collectLatest
          }

          when (snackbarClickable.key as SnackbarButton) {
            SnackbarButton.ReloadLastVisitedCatalog -> {
              val catalogDescriptor = snackbarClickable.data as? CatalogDescriptor
                ?: return@collectLatest

              catalogScreenViewModel.loadCatalog(catalogDescriptor)
            }
            SnackbarButton.ReloadLastVisitedThread -> {
              val threadDescriptor = snackbarClickable.data as? ThreadDescriptor
                ?: return@collectLatest

              threadScreenViewModel.loadThread(threadDescriptor)
            }
          }
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        lastVisitedEndpointManager.lastVisitedCatalogFlow.collect { lastVisitedCatalog ->
          snackbarManager.pushSnackbar(
            SnackbarInfo(
              snackbarId = SnackbarId.ReloadLastVisitedCatalog,
              aliveUntil = SnackbarInfo.snackbarDuration(10.seconds),
              content = listOf(
                SnackbarContentItem.Text(
                  context.getString(
                    R.string.restore_last_visited_catalog,
                    lastVisitedCatalog.toCatalogDescriptor().asReadableString()
                  )
                ),
                SnackbarContentItem.Spacer(space = 8.dp),
                SnackbarContentItem.Button(
                  key = SnackbarButton.ReloadLastVisitedCatalog,
                  text = context.getString(R.string.restore),
                  data = lastVisitedCatalog.toCatalogDescriptor()
                ),
                SnackbarContentItem.Spacer(space = 8.dp),
              )
            )
          )
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        lastVisitedEndpointManager.lastVisitedThreadFlow.collect { lastVisitedThread ->
          val threadDescriptor = lastVisitedThread.toThreadDescriptor()

          val lastVisitedThreadTitle = if (lastVisitedThread.title.isNotNullNorBlank()) {
            "${threadDescriptor.siteKeyActual}/${threadDescriptor.boardCode}/${lastVisitedThread.title}"
          } else {
            threadDescriptor.asReadableString()
          }

          snackbarManager.pushSnackbar(
            SnackbarInfo(
              screenKey = MainScreen.SCREEN_KEY,
              snackbarId = SnackbarId.ReloadLastVisitedThread,
              aliveUntil = SnackbarInfo.snackbarDuration(10.seconds),
              content = listOf(
                SnackbarContentItem.Text(
                  context.getString(
                    R.string.restore_last_visited_thread,
                    lastVisitedThreadTitle
                  )
                ),
                SnackbarContentItem.Spacer(space = 8.dp),
                SnackbarContentItem.Button(
                  key = SnackbarButton.ReloadLastVisitedThread,
                  text = context.getString(R.string.restore),
                  data = threadDescriptor
                ),
                SnackbarContentItem.Spacer(space = 8.dp),
              )
            )
          )
        }
      }
    )
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  private fun HomeScreenContentActual(
    maxDrawerWidth: Int,
    mainUiLayoutMode: MainUiLayoutMode,
    drawerPhoneVisibleWindowWidth: Int,
    drawerLongtapGestureWidthZonePx: Float,
    pagerState: PagerState,
    childScreens: HomeChildScreens.ChildScreens,
    insets: Insets,
    chanTheme: ChanTheme
  ) {
    val windowInsets = LocalWindowInsets.current
    val density = LocalDensity.current
    val draggableAreaSize = 350f
    val coroutineScope = rememberCoroutineScope()

    var drawerWidth by remember { mutableStateOf(0) }
    var homeScreenSize by remember { mutableStateOf(IntSize.Zero) }
    var consumeAllScrollEvents by remember { mutableStateOf(false) }
    var longtapDragGestureDetected by remember { mutableStateOf(false) }
    var failedDrawerDragGestureDetected by remember { mutableStateOf(false) }

    val pagerSwipeExclusionZone = remember(key1 = homeScreenSize) {
      if (homeScreenSize.width > 0 && homeScreenSize.height > 0) {
        val bottomInsetPx = with(density) { windowInsets.bottom.roundToPx() }

        Rect(
          offset = Offset(0f, homeScreenSize.height - draggableAreaSize - bottomInsetPx),
          size = Size(draggableAreaSize, draggableAreaSize)
        )
      } else {
        Rect.Zero
      }
    }

    val currentScreen = remember(key1 = pagerState.currentPage) {
      childScreens.screens.getOrNull(pagerState.currentPage)
    }

    val nestedScrollConnection = remember(key1 = drawerWidth) {
      HomePagerNestedScrollConnection(
        currentPagerPage = { pagerState.currentPage },
        isGestureCurrentlyAllowed = { isDrawerDragGestureCurrentlyAllowed(currentScreen, true) },
        shouldConsumeAllScrollEvents = { consumeAllScrollEvents },
        onDragging = { dragging, progress -> globalUiInfoManager.dragDrawer(dragging, progress) }
      )
    }

    Box(
      modifier = Modifier
        .onSizeChanged { size ->
          homeScreenSize = size
          drawerWidth = size.width.coerceAtMost(maxDrawerWidth) - drawerPhoneVisibleWindowWidth
        }
        .pointerInput(
          drawerLongtapGestureWidthZonePx,
          drawerPhoneVisibleWindowWidth,
          drawerWidth,
          pagerSwipeExclusionZone,
          block = {
            detectDrawerDragGestures(
              drawerLongtapGestureWidthZonePx = drawerLongtapGestureWidthZonePx,
              drawerPhoneVisibleWindowWidthPx = drawerPhoneVisibleWindowWidth.toFloat(),
              drawerWidth = drawerWidth.toFloat(),
              pagerSwipeExclusionZone = pagerSwipeExclusionZone,
              isDrawerOpened = { globalUiInfoManager.isDrawerFullyOpened() },
              onStopConsumingScrollEvents = { consumeAllScrollEvents = false },
              isGestureCurrentlyAllowed = { isDrawerDragGestureCurrentlyAllowed(currentScreen, false) },
              onLongtapDragGestureDetected = { longtapDragGestureDetected = true },
              onFailedDrawerDragGestureDetected = { failedDrawerDragGestureDetected = true },
              onDraggingDrawer = { dragging, progress -> globalUiInfoManager.dragDrawer(dragging, progress) }
            )
          }
        )
        .nestedScroll(nestedScrollConnection)
        .drawPagerSwipeExclusionZoneTutorial(
          failedDrawerDragGestureDetected = failedDrawerDragGestureDetected,
          pagerSwipeExclusionZone = pagerSwipeExclusionZone,
          onTutorialFinished = { coroutineScope.launch { showPagerSwipeExclusionZoneDialog() } },
          resetFlag = { failedDrawerDragGestureDetected = false }
        )
        .drawDragLongtapDragGestureZone(
          drawerLongtapGestureWidthZonePx = drawerLongtapGestureWidthZonePx,
          longtapDragGestureDetected = longtapDragGestureDetected,
          resetFlag = { longtapDragGestureDetected = false }
        )
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
        mainUiLayoutMode = mainUiLayoutMode
      )

      HomeScreenFloatingActionButton(
        insets = insets,
        pagerState = pagerState,
        childScreens = childScreens.screens,
        mainUiLayoutMode = mainUiLayoutMode,
        onFabClicked = { screenKey -> homeScreenViewModel.onHomeScreenFabClicked(screenKey) }
      )

      if (drawerWidth > 0) {
        HomeScreenDrawerLayout(
          drawerWidth = drawerWidth,
          navigationRouter = navigationRouter,
        )
      }
    }
  }

  private fun showPagerSwipeExclusionZoneDialog() {
    navigationRouter.presentScreen(
      DialogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        params = DialogScreen.Params(
          title = DialogScreen.Text.Id(R.string.pager_exclusion_zone_dialog_title),
          description = DialogScreen.Text.Id(R.string.pager_exclusion_zone_dialog_description),
          positiveButton = DialogScreen.okButton()
        )
      )
    )
  }

  private fun isDrawerDragGestureCurrentlyAllowed(
    currentScreen: ComposeScreenWithToolbar?,
    isFromNestedScroll: Boolean
  ): Boolean {
    if (currentScreen is ScreenLayout<*>) {
      if (isFromNestedScroll) {
        if (currentScreen.anyScreenHasChildren()) {
          return false
        }
      } else {
        if (currentScreen.screenHasChildren(CatalogScreen.SCREEN_KEY)) {
          return false
        }
      }
    } else {
      if (currentScreen == null || currentScreen.hasChildScreens()) {
        return false
      }
    }

    if (globalUiInfoManager.isAnyReplyLayoutOpened()) {
      return false
    }

    return true
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

  enum class SnackbarButton {
    ReloadLastVisitedCatalog,
    ReloadLastVisitedThread,
  }

  companion object {
    val SCREEN_KEY = ScreenKey("HomeScreen")
  }
}