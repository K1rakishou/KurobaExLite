package com.github.k1rakishou.kurobaexlite.features.home

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.drawer.HomeScreenDrawerLayout
import com.github.k1rakishou.kurobaexlite.features.drawer.detectDrawerDragGestures
import com.github.k1rakishou.kurobaexlite.features.firewall.BypassResult
import com.github.k1rakishou.kurobaexlite.features.firewall.SiteFirewallBypassScreen
import com.github.k1rakishou.kurobaexlite.features.home.pages.AbstractPage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SinglePage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SplitPage
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.resumeSafe
import com.github.k1rakishou.kurobaexlite.managers.FirewallBypassManager
import com.github.k1rakishou.kurobaexlite.managers.LastVisitedEndpointManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.drawDragLongtapDragGestureZone
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.drawPagerSwipeExclusionZoneTutorial
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class HomeScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val homeScreenPageConverter by lazy { HomeScreenPageConverter(componentActivity, navigationRouter) }
  private val siteManager: SiteManager by inject(SiteManager::class.java)
  private val lastVisitedEndpointManager: LastVisitedEndpointManager by inject(LastVisitedEndpointManager::class.java)
  private val firewallBypassManager: FirewallBypassManager by inject(FirewallBypassManager::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY

  @Suppress("UnnecessaryVariable")
  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val context = LocalContext.current

    ShowAndProcessSnackbars()

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

    ListenForFirewallBypassManagerEvents()

    if (!globalUiInfoViewModelInitialized) {
      return
    }

    val mainUiLayoutModeMut by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut
    if (mainUiLayoutMode == null) {
      return
    }

    val historyEnabled by globalUiInfoManager.historyEnabled.collectAsState()
    val historyScreenOnLeftSide by globalUiInfoManager.historyScreenOnLeftSide.collectAsState()
    val currentScreenPage by globalUiInfoManager.currentPageFlow(mainUiLayoutMode).collectAsState()

    val pagesWrapper = remember(
      key1 = historyScreenOnLeftSide,
      key2 = mainUiLayoutMode,
      key3 = historyEnabled
    ) {
      return@remember homeScreenPageConverter.convertScreensToPages(
        uiLayoutMode = mainUiLayoutMode,
        historyEnabled = historyEnabled,
        historyScreenOnLeftSide = historyScreenOnLeftSide
      )
    }
    val pagesWrapperUpdated by rememberUpdatedState(newValue = pagesWrapper)

    val initialScreenIndexMut = remember(
      key1 = currentScreenPage.screenKey,
      key2 = pagesWrapper
    ) {
      return@remember pagesWrapper.screenIndexByScreenKey(currentScreenPage.screenKey)
    }

    val initialScreenIndex = initialScreenIndexMut
    if (initialScreenIndex == null) {
      logcat(TAG, LogPriority.WARN) {
        "initialScreenIndex is null. " +
          "currentScreenPage.screenKey=${currentScreenPage.screenKey}, " +
          "pagesWrapper.pagesCount=${pagesWrapper.pagesCount}"
      }

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
    val pagerState = rememberPagerState(
      key1 = orientation,
      initialPage = initialScreenIndex
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        globalUiInfoManager.notEnoughWidthForSplitLayoutFlow
          .debounce(500.milliseconds)
          .collect { info ->
            if (info == null) {
              return@collect
            }

            val (minCatalogSplitModelWidth, availableWidthForCatalog) = info

            val errorMessage = context.resources.getString(
              R.string.not_enough_with_for_split_layout_mode,
              minCatalogSplitModelWidth,
              availableWidthForCatalog
            )

            snackbarManager.errorToast(
              message = errorMessage,
              toastId = "not_enough_width_for_split_layout_mode",
              screenKey = MainScreen.SCREEN_KEY,
              duration = 4000.milliseconds
            )
          }
      }
    )

    val pageCount by remember { derivedStateOf { pagerState.pageCount } }
    val currentPageIndex by remember { derivedStateOf { pagerState.currentPage } }
    val targetPageIndex by remember { derivedStateOf { pagerState.targetPage } }

    LaunchedEffect(
      key1 = mainUiLayoutMode,
      key2 = historyScreenOnLeftSide,
      key3 = pageCount,
      block = {
        if (pageCount <= 0) {
          return@LaunchedEffect
        }

        globalUiInfoManager.currentPageFlow(mainUiLayoutMode).collect { currentPage ->
          scrollToPageByScreenKey(
            screenKey = currentPage.screenKey,
            pagesWrapper = pagesWrapper,
            pagerState = pagerState,
            animate = currentPage.animate
          )
        }
      }
    )

    LaunchedEffect(
      key1 = currentPageIndex,
      key2 = mainUiLayoutMode,
      key3 = pageCount,
      block = {
        if (pageCount <= 0) {
          return@LaunchedEffect
        }

        val screenKey = pagesWrapper.screenKeyByPageIndex(currentPageIndex)
          ?: return@LaunchedEffect

        globalUiInfoManager.updateCurrentPageForLayoutMode(
          screenKey = screenKey,
          mainUiLayoutMode = mainUiLayoutMode
        )
      }
    )

    HandleBackPresses {
      if (globalUiInfoManager.isDrawerOpenedOrOpening()) {
        globalUiInfoManager.closeDrawer()
        return@HandleBackPresses true
      }

      val currentMainUiLayoutMode = globalUiInfoManager.currentUiLayoutModeState.value
        ?: return@HandleBackPresses false

      val freshCurrentPageScreenKey = globalUiInfoManager.currentPage(currentMainUiLayoutMode)?.screenKey
        ?: return@HandleBackPresses false

      // First, process all child screens
      val currentScreenIndex = pagesWrapperUpdated.screenIndexByScreenKey(freshCurrentPageScreenKey)
      if (currentScreenIndex != null) {
        val currentPage = pagesWrapperUpdated.pageByIndex(currentScreenIndex)
        if (currentPage != null) {
          for (childScreen in currentPage.childScreens.asReversed()) {
            if (childScreen.composeScreen.onBackPressed()) {
              return@HandleBackPresses true
            }
          }
        }
      }

      // Then reset the ViewPager's current page
      if (!homeScreenPageConverter.isMainScreen(freshCurrentPageScreenKey)) {
        globalUiInfoManager.updateCurrentPage(homeScreenPageConverter.mainScreenKey())
        return@HandleBackPresses true
      }

      return@HandleBackPresses false
    }

    HomeScreenContentActual(
      maxDrawerWidth = maxDrawerWidth,
      currentPageIndex = currentPageIndex,
      targetPageIndex = targetPageIndex,
      mainUiLayoutMode = mainUiLayoutMode,
      drawerPhoneVisibleWindowWidth = drawerPhoneVisibleWindowWidth,
      drawerLongtapGestureWidthZonePx = drawerLongtapGestureWidthZonePx,
      pagerState = pagerState,
      pagesWrapper = pagesWrapper,
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
        snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
          if (snackbarClickable.key !is SnackbarButton) {
            return@collectLatest
          }

          when (snackbarClickable.key as SnackbarButton) {
            SnackbarButton.ReloadLastVisitedCatalog -> {
              val catalogDescriptor = snackbarClickable.data as? CatalogDescriptor
                ?: return@collectLatest

              catalogScreenViewModel.loadCatalog(catalogDescriptor)
              globalUiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY)
            }
            SnackbarButton.ReloadLastVisitedThread -> {
              val threadDescriptor = snackbarClickable.data as? ThreadDescriptor
                ?: return@collectLatest

              threadScreenViewModel.loadThread(threadDescriptor)
              globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
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
              aliveUntil = SnackbarInfo.snackbarDuration(7.seconds),
              content = listOf(
                SnackbarContentItem.Text(
                  context.getString(
                    R.string.restore_last_visited_catalog,
                    lastVisitedCatalog.catalogDescriptor.asReadableString()
                  )
                ),
                SnackbarContentItem.Spacer(space = 8.dp),
                SnackbarContentItem.Button(
                  key = SnackbarButton.ReloadLastVisitedCatalog,
                  text = context.getString(R.string.restore),
                  data = lastVisitedCatalog.catalogDescriptor
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
          val threadDescriptor = lastVisitedThread.threadDescriptor

          val lastVisitedThreadTitle = if (lastVisitedThread.title.isNotNullNorBlank()) {
            "${threadDescriptor.siteKeyActual}/${threadDescriptor.boardCode}/${lastVisitedThread.title}"
          } else {
            threadDescriptor.asReadableString()
          }

          snackbarManager.pushSnackbar(
            SnackbarInfo(
              screenKey = MainScreen.SCREEN_KEY,
              snackbarId = SnackbarId.ReloadLastVisitedThread,
              aliveUntil = SnackbarInfo.snackbarDuration(7.seconds),
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
    currentPageIndex: Int,
    targetPageIndex: Int,
    mainUiLayoutMode: MainUiLayoutMode,
    drawerPhoneVisibleWindowWidth: Int,
    drawerLongtapGestureWidthZonePx: Float,
    pagerState: PagerState,
    pagesWrapper: HomeScreenPageConverter.PagesWrapper,
    insets: Insets,
    chanTheme: ChanTheme
  ) {
    val windowInsets = LocalWindowInsets.current
    val density = LocalDensity.current
    val view = LocalView.current
    // TODO(KurobaEx): extract into settings
    val draggableAreaSize = remember { with(density) { Size(width = 150.dp.toPx(), height = 80.dp.toPx()) } }
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
          offset = Offset(0f, homeScreenSize.height - draggableAreaSize.height - bottomInsetPx),
          size = draggableAreaSize
        )
      } else {
        Rect.Zero
      }
    }

    val currentPageMut = remember(key1 = currentPageIndex, key2 = pagesWrapper) {
      pagesWrapper.pageByIndex(currentPageIndex)
    }

    val currentPage = currentPageMut
    if (currentPage == null) {
      logcat(TAG, LogPriority.WARN) {
        "currentPage is null. " +
          "currentPageIndex=${currentPageIndex}, " +
          "pagesWrapper.pagesCount=${pagesWrapper.pagesCount}"
      }

      return
    }

    val currentPageUpdated by rememberUpdatedState(newValue = currentPage)
    val currentPageIndexUpdated by rememberUpdatedState(newValue = currentPageIndex)

    val nestedScrollConnection = remember(key1 = drawerWidth) {
      HomePagerNestedScrollConnection(
        currentPagerPage = { currentPageIndexUpdated },
        isGestureCurrentlyAllowed = {
          isDrawerDragGestureCurrentlyAllowed(
            currentPageIndex = currentPageIndexUpdated,
            currentPage = currentPageUpdated,
            isFromNestedScroll = true,
            isInsideSpecialZone = false
          )
        },
        shouldConsumeAllScrollEvents = { consumeAllScrollEvents },
        onDragging = { dragging, time, progress -> globalUiInfoManager.dragDrawer(dragging, time, progress) },
        onFling = { velocity -> globalUiInfoManager.flingDrawer(velocity) }
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
              currentPagerPage = { currentPageIndex },
              isDrawerOpened = { globalUiInfoManager.isDrawerFullyOpened() },
              onStopConsumingScrollEvents = { consumeAllScrollEvents = false },
              isGestureCurrentlyAllowed = { insideSpecialZone ->
                isDrawerDragGestureCurrentlyAllowed(
                  currentPageIndex = currentPageIndexUpdated,
                  currentPage = currentPageUpdated,
                  isFromNestedScroll = false,
                  isInsideSpecialZone = insideSpecialZone
                )
              },
              onLongtapDragGestureDetected = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                longtapDragGestureDetected = true
              },
              onFailedDrawerDragGestureDetected = { failedDrawerDragGestureDetected = true },
              onDraggingDrawer = { dragging, time, progress ->
                globalUiInfoManager.dragDrawer(dragging, time, progress)
              }
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
        count = pagesWrapper.pagesCount,
        key = { index -> pagesWrapper.pageByIndex(index)!!.screenKey()}
      ) { page ->
        val childPage = remember(key1 = page) { pagesWrapper.pageByIndex(page) }
          ?: return@HorizontalPager

        val transitionIsProgress by remember { derivedStateOf { currentPageIndex != targetPageIndex } }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .consumeClicks(enabled = transitionIsProgress)
        ) {
          childPage.Content()
        }
      }

      HomeScreenToolbarContainer(
        insets = insets,
        chanTheme = chanTheme,
        pagerState = pagerState,
        pagesWrapper = pagesWrapper,
        mainUiLayoutMode = mainUiLayoutMode
      )

      HomeScreenFloatingActionButton(
        insets = insets,
        pagerState = pagerState,
        pagesWrapper = pagesWrapper,
        mainUiLayoutMode = mainUiLayoutMode,
        onFabClicked = { screenKey -> homeScreenViewModel.onHomeScreenFabClicked(screenKey) }
      )

      if (drawerWidth > 0) {
        HomeScreenDrawerLayout(
          drawerWidth = drawerWidth,
          navigationRouterProvider = { navigationRouter },
        )
      }
    }
  }

  @Composable
  private fun ListenForFirewallBypassManagerEvents() {
    LaunchedEffect(
      key1 = Unit,
      block = {
        firewallBypassManager.showFirewallControllerEvents.collect { showFirewallControllerInfo ->
          val siteFirewallBypassScreen = navigationRouter.getScreenByKey(SiteFirewallBypassScreen.SCREEN_KEY)

          val screenIsAlive = when (siteFirewallBypassScreen?.screenLifecycle) {
            null -> false
            ScreenLifecycle.Creating,
            ScreenLifecycle.Created -> true
            ScreenLifecycle.Disposing,
            ScreenLifecycle.Disposed -> false
          }

          if (screenIsAlive) {
            return@collect
          }

          val firewallType = showFirewallControllerInfo.firewallType
          val urlToOpen = showFirewallControllerInfo.urlToOpen
          val siteKey = showFirewallControllerInfo.siteKey
          val onFinished = showFirewallControllerInfo.onFinished

          try {
            showSiteFirewallBypassController(
              firewallType = firewallType,
              urlToOpen = urlToOpen,
              siteKey = siteKey
            )
          } finally {
            onFinished.complete(Unit)
          }
        }
      }
    )
  }

  private suspend fun showSiteFirewallBypassController(
    firewallType: FirewallType,
    urlToOpen: HttpUrl,
    siteKey: SiteKey
  ) {
    logcat(TAG) { "Launching SiteFirewallBypassScreen" }

    val bypassResult = suspendCancellableCoroutine<BypassResult> { continuation ->
      val siteFirewallBypassScreen = createScreen<SiteFirewallBypassScreen>(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        args = {
          putSerializable(
            SiteFirewallBypassScreen.FIREWALL_TYPE,
            firewallType
          )
          putSerializable(SiteFirewallBypassScreen.URL_TO_OPEN, urlToOpen.toString())
        },
        callbacks = {
          callback<BypassResult>(
            callbackKey = SiteFirewallBypassScreen.ON_RESULT,
            func = { bypassResult -> continuation.resumeSafe(bypassResult) }
          )
        }
      )

      navigationRouter.presentScreen(siteFirewallBypassScreen)
    }

    logcat(TAG) { "SiteFirewallBypassScreen finished" }


    when (firewallType) {
      FirewallType.Cloudflare -> {
        if (bypassResult is BypassResult.Cookie && bypassResult.cookie.isNotEmpty()) {
          logcat(TAG) { "Got ${firewallType} cookies, cookieResult: ${bypassResult}" }

          siteManager.bySiteKey(siteKey)?.siteSettings?.cloudFlareClearanceCookie
            ?.write(bypassResult.cookie)
        } else {
          logcatError(TAG) { "Failed to bypass ${firewallType}, bypassResult: ${bypassResult}" }
        }
      }
      FirewallType.YandexSmartCaptcha -> {
        // No-op. We only handle Yandex's captcha in one place (ImageSearchController)
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
        ),
        canDismissByClickingOutside = false
      )
    )
  }

  private fun isDrawerDragGestureCurrentlyAllowed(
    currentPageIndex: Int,
    currentPage: AbstractPage<ComposeScreenWithToolbar<*>>?,
    isFromNestedScroll: Boolean,
    isInsideSpecialZone: Boolean
  ): Boolean {
    if (currentPageIndex == 0 && isInsideSpecialZone) {
      return false
    }

    if (currentPage is SplitPage) {
      if (isFromNestedScroll) {
        if (!currentPage.canDragPager()) {
          return false
        }
      } else {
        if (currentPage.screenHasChildren(CatalogScreen.SCREEN_KEY)) {
          return false
        }
      }
    } else {
      if (currentPage == null || !currentPage.canDragPager()) {
        return false
      }
    }

    when (currentPage) {
      is SinglePage -> {
        if (globalUiInfoManager.isReplyLayoutOpened(currentPage.screenKey())) {
          return false
        }
      }
      is SplitPage -> {
        val anyReplyLayoutsOpened = currentPage.childScreens
          .any { childScreen -> globalUiInfoManager.isReplyLayoutOpened(childScreen.screenKey) }

        if (anyReplyLayoutsOpened) {
          return false
        }
      }
      null -> {
        return false
      }
    }

    return true
  }

  @OptIn(ExperimentalPagerApi::class)
  private suspend fun scrollToPageByScreenKey(
    screenKey: ScreenKey,
    pagesWrapper: HomeScreenPageConverter.PagesWrapper,
    pagerState: PagerState,
    animate: Boolean
  ) {
    val indexOfPage = pagesWrapper.screenIndexByScreenKey(screenKey) ?: -1
    if (indexOfPage < 0) {
      return
    }

    try {
      if (animate) {
        pagerState.animateScrollToPage(page = indexOfPage)
      } else {
        pagerState.scrollToPage(page = indexOfPage)
      }
    } catch (error: CancellationException) {
      // consumed to avoid the flow collection cancellation
    }
  }

  enum class SnackbarButton {
    ReloadLastVisitedCatalog,
    ReloadLastVisitedThread,
  }

  companion object {
    private const val TAG = "HomeScreen"
    val SCREEN_KEY = ScreenKey("HomeScreen")
  }
}