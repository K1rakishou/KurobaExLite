package com.github.k1rakishou.kurobaexlite.features.home

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.drawer.HomeScreenDrawerLayout
import com.github.k1rakishou.kurobaexlite.features.drawer.HomeScreenMiniDrawerLayout
import com.github.k1rakishou.kurobaexlite.features.drawer.detectDrawerDragGestures
import com.github.k1rakishou.kurobaexlite.features.firewall.BypassResult
import com.github.k1rakishou.kurobaexlite.features.firewall.SiteFirewallBypassScreen
import com.github.k1rakishou.kurobaexlite.features.home.pages.AbstractPage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SinglePage
import com.github.k1rakishou.kurobaexlite.features.home.pages.SplitPage
import com.github.k1rakishou.kurobaexlite.features.main.LocalMainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.FirewallBypassManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.LastVisitedEndpointManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowSizeClass
import com.github.k1rakishou.kurobaexlite.ui.helpers.WindowSizeClass
import com.github.k1rakishou.kurobaexlite.ui.helpers.WindowWidthSizeClass
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.drawDragLongtapDragGestureZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HomeScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val homeScreenPageConverter by lazy { HomeScreenPageConverter(componentActivity, navigationRouter) }
  private val siteManager: SiteManager by inject(SiteManager::class.java)
  private val firewallBypassManager: FirewallBypassManager by inject(FirewallBypassManager::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY

  @Suppress("UnnecessaryVariable")
  @Composable
  override fun Content() {
    ContentInternal(
      isDrawerDragGestureCurrentlyAllowed = { currentPage, isFromNestedScroll ->
        isDrawerDragGestureCurrentlyAllowed(
          currentPage = currentPage,
          isFromNestedScroll = isFromNestedScroll
        )
      },
      navigationRouterProvider = { navigationRouter },
      homeScreenPageConverterProvider = { homeScreenPageConverter },
      showSiteFirewallBypassController = { firewallType, urlToOpen, originalRequestUrl, siteKey ->
        showSiteFirewallBypassController(
          firewallType = firewallType,
          urlToOpen = urlToOpen,
          originalRequestUrl = originalRequestUrl,
          siteKey = siteKey
        )
      },
      handleBackPresses = { pagesWrapper ->
        val mainUiLayoutMode = LocalMainUiLayoutMode.current

        val pagesWrapperUpdated by rememberUpdatedState(newValue = pagesWrapper)
        val mainUiLayoutModeUpdated by rememberUpdatedState(newValue = mainUiLayoutMode)

        HandleBackPresses {
          if (globalUiInfoManager.isDrawerOpenedOrOpening()) {
            globalUiInfoManager.closeDrawer()
            return@HandleBackPresses true
          }

          val freshCurrentPageScreenKey = globalUiInfoManager.currentPage(mainUiLayoutModeUpdated)?.screenKey
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
      }
    )
  }

  private suspend fun showSiteFirewallBypassController(
    firewallType: FirewallType,
    urlToOpen: HttpUrl,
    originalRequestUrl: HttpUrl,
    siteKey: SiteKey
  ) {
    logcat(TAG) { "Launching SiteFirewallBypassScreen(${firewallType}, ${urlToOpen}, ${originalRequestUrl}, ${siteKey})" }

    val bypassResult = suspendCancellableCoroutine<BypassResult> { continuation ->
      val siteFirewallBypassScreen = ComposeScreen.createScreen<SiteFirewallBypassScreen>(
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

    when (firewallType) {
      FirewallType.Cloudflare -> {
        if (bypassResult is BypassResult.Cookie && bypassResult.cookie.isNotEmpty()) {
          logcat(TAG) { "Got ${firewallType} cookies, cookieResult: ${bypassResult}" }

          siteManager.bySiteKey(siteKey)
            ?.siteSettings
            ?.cloudFlareClearanceCookie
            ?.put(bypassResult.domainOrHost, bypassResult.cookie)
        } else {
          logcatError(TAG) { "Failed to bypass ${firewallType}, bypassResult: ${bypassResult}" }
        }
      }
      FirewallType.YandexSmartCaptcha -> {
        // No-op. We only handle Yandex's captcha in one place (ImageSearchController)
      }
    }

    if (bypassResult is BypassResult.Cookie) {
      firewallBypassManager.onFirewallBypassed(
        firewallType = firewallType,
        siteKey = siteKey,
        urlToOpen = urlToOpen,
        originalRequestUrl = originalRequestUrl
      )
    }

    logcat(TAG) { "Finished SiteFirewallBypassScreen(${firewallType}, ${urlToOpen}, ${originalRequestUrl}, ${siteKey})" }
  }

  private fun isDrawerDragGestureCurrentlyAllowed(
    currentPage: AbstractPage<ComposeScreenWithToolbar<*>>?,
    isFromNestedScroll: Boolean
  ): Boolean {
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


  enum class SnackbarButton {
    ReloadLastVisitedCatalog,
    ReloadLastVisitedThread,
  }

  companion object {
    internal const val TAG = "HomeScreen"
    val SCREEN_KEY = ScreenKey("HomeScreen")
  }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun ContentInternal(
  isDrawerDragGestureCurrentlyAllowed: (AbstractPage<ComposeScreenWithToolbar<*>>, Boolean) -> Boolean,
  navigationRouterProvider: () -> NavigationRouter,
  homeScreenPageConverterProvider: () -> HomeScreenPageConverter,
  showSiteFirewallBypassController: suspend (FirewallType, HttpUrl, HttpUrl, SiteKey) -> Unit,
  handleBackPresses: @Composable (HomeScreenPageConverter.PagesWrapper) -> Unit
) {
  val context = LocalContext.current

  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val snackbarManager: SnackbarManager = koinRemember()

  ShowAndProcessSnackbars()

  val orientation = LocalConfiguration.current.orientation
  val mainUiLayoutMode = LocalMainUiLayoutMode.current

  ListenForFirewallBypassManagerEvents(
    navigationRouterProvider = navigationRouterProvider,
    showSiteFirewallBypassController = showSiteFirewallBypassController,
  )

  val currentScreenPage by globalUiInfoManager.currentPageFlow(mainUiLayoutMode).collectAsState()

  val pagesWrapper = remember(key1 = mainUiLayoutMode) {
    return@remember homeScreenPageConverterProvider().convertScreensToPages(mainUiLayoutMode)
  }

  val initialScreenIndexMut = remember(
    key1 = currentScreenPage.screenKey,
    key2 = pagesWrapper
  ) {
    return@remember pagesWrapper.screenIndexByScreenKey(currentScreenPage.screenKey)
  }

  val initialScreenIndex = initialScreenIndexMut
  if (initialScreenIndex == null) {
    logcat(HomeScreen.TAG, LogPriority.WARN) {
      "initialScreenIndex is null. " +
        "currentScreenPage.screenKey=${currentScreenPage.screenKey}, " +
        "pagesWrapper.pagesCount=${pagesWrapper.pagesCount}"
    }

    return
  }

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
    key2 = pageCount,
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

  handleBackPresses(pagesWrapper)

  HomeScreenContentActual(
    currentPageIndex = currentPageIndex,
    targetPageIndex = targetPageIndex,
    mainUiLayoutMode = mainUiLayoutMode,
    pagerState = pagerState,
    pagesWrapper = pagesWrapper,
    isDrawerDragGestureCurrentlyAllowed = isDrawerDragGestureCurrentlyAllowed,
    navigationRouterProvider = navigationRouterProvider,
  )
}

@Composable
private fun ShowAndProcessSnackbars() {
  val context = LocalContext.current

  val catalogScreenViewModel: CatalogScreenViewModel = koinRememberViewModel()
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val snackbarManager: SnackbarManager = koinRemember()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()
  val lastVisitedEndpointManager: LastVisitedEndpointManager = koinRemember()

  LaunchedEffect(
    key1 = Unit,
    block = {
      snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
        if (snackbarClickable.key !is HomeScreen.SnackbarButton) {
          return@collectLatest
        }

        when (snackbarClickable.key as HomeScreen.SnackbarButton) {
          HomeScreen.SnackbarButton.ReloadLastVisitedCatalog -> {
            val catalogDescriptor = snackbarClickable.data as? CatalogDescriptor
              ?: return@collectLatest

            catalogScreenViewModel.loadCatalog(catalogDescriptor)
            globalUiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY)
          }
          HomeScreen.SnackbarButton.ReloadLastVisitedThread -> {
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
                key = HomeScreen.SnackbarButton.ReloadLastVisitedCatalog,
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
                key = HomeScreen.SnackbarButton.ReloadLastVisitedThread,
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
  currentPageIndex: Int,
  targetPageIndex: Int,
  mainUiLayoutMode: MainUiLayoutMode,
  pagerState: PagerState,
  pagesWrapper: HomeScreenPageConverter.PagesWrapper,
  isDrawerDragGestureCurrentlyAllowed: (AbstractPage<ComposeScreenWithToolbar<*>>, Boolean) -> Boolean,
  navigationRouterProvider: () -> NavigationRouter,
) {
  val windowInsets = LocalWindowInsets.current
  val view = LocalView.current
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current
  val windowSizeClass = LocalWindowSizeClass.current

  val drawerLongtapGestureWidthZonePx = with(density) { remember { 24.dp.toPx() } }
  val maxDrawerWidth = with(density) { 600.dp.roundToPx() }
  val drawerPhoneVisibleWindowWidth = with(density) { remember { 40.dp.toPx().toInt() } }

  val homeScreenViewModel: HomeScreenViewModel = koinRememberViewModel()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  var drawerWidth by remember { mutableStateOf(0) }
  var consumeAllScrollEvents by remember { mutableStateOf(false) }
  var longtapDragGestureDetected by remember { mutableStateOf(false) }

  val currentPageMut = remember(key1 = currentPageIndex, key2 = pagesWrapper) {
    pagesWrapper.pageByIndex(currentPageIndex)
  }

  val currentPage = currentPageMut
  if (currentPage == null) {
    logcat(HomeScreen.TAG, LogPriority.WARN) {
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
      isGestureCurrentlyAllowed = { isDrawerDragGestureCurrentlyAllowed(currentPageUpdated, true) },
      shouldConsumeAllScrollEvents = { consumeAllScrollEvents },
      onDragging = { dragging, time, progress -> globalUiInfoManager.dragDrawer(dragging, time, progress) },
      onFling = { velocity -> globalUiInfoManager.flingDrawer(velocity) }
    )
  }

  Row {
    var miniDrawerDisplayed by remember { mutableStateOf(false) }

    HomeScreenMiniDrawer(
      mainUiLayoutMode = mainUiLayoutMode,
      windowSizeClass = windowSizeClass,
      onMiniDrawerDisplayFlagChanged = { nowDisplayed -> miniDrawerDisplayed = nowDisplayed }
    )

    Box(
      modifier = Modifier
        .onSizeChanged { size ->
          drawerWidth = size.width.coerceAtMost(maxDrawerWidth) - drawerPhoneVisibleWindowWidth
        }
        .pointerInput(
          drawerLongtapGestureWidthZonePx,
          drawerPhoneVisibleWindowWidth,
          drawerWidth,
          block = {
            detectDrawerDragGestures(
              drawerLongtapGestureWidthZonePx = drawerLongtapGestureWidthZonePx,
              drawerPhoneVisibleWindowWidthPx = drawerPhoneVisibleWindowWidth.toFloat(),
              drawerWidth = drawerWidth.toFloat(),
              currentPagerPage = { currentPageIndex },
              isDrawerOpened = { globalUiInfoManager.isDrawerFullyOpened() },
              onStopConsumingScrollEvents = { consumeAllScrollEvents = false },
              isGestureCurrentlyAllowed = {
                isDrawerDragGestureCurrentlyAllowed(
                  currentPageUpdated,
                  false
                )
              },
              onLongtapDragGestureDetected = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                longtapDragGestureDetected = true
              },
              onDraggingDrawer = { dragging, time, progress ->
                globalUiInfoManager.dragDrawer(dragging, time, progress)
              }
            )
          }
        )
        .nestedScroll(nestedScrollConnection)
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
        insets = windowInsets,
        chanTheme = chanTheme,
        pagerState = pagerState,
        pagesWrapper = pagesWrapper,
        mainUiLayoutMode = mainUiLayoutMode
      )

      HomeScreenFloatingActionButton(
        insets = windowInsets,
        pagerState = pagerState,
        pagesWrapper = pagesWrapper,
        mainUiLayoutMode = mainUiLayoutMode,
        onFabClicked = { screenKey -> homeScreenViewModel.onHomeScreenFabClicked(screenKey) }
      )

      if (drawerWidth > 0) {
        HomeScreenDrawerLayout(
          drawerWidth = drawerWidth,
          miniDrawerDisplayed = miniDrawerDisplayed,
          navigationRouterProvider = navigationRouterProvider,
        )
      }

      LaunchedEffect(
        key1 = Unit,
        block = {
          try {
            delay(250L)
          } finally {
            globalUiInfoManager.onPagerDisplayed()
          }
        }
      )
    }
  }
}

@Composable
private fun RowScope.HomeScreenMiniDrawer(
  mainUiLayoutMode: MainUiLayoutMode,
  windowSizeClass: WindowSizeClass,
  onMiniDrawerDisplayFlagChanged: (Boolean) -> Unit
) {
  val bookmarksManager: BookmarksManager = koinRemember()
  var hasAnyBookmarks by remember { mutableStateOf(false) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      hasAnyBookmarks = bookmarksManager.hasBookmarks()

      bookmarksManager.bookmarkEventsFlow.collect { event ->
        when (event) {
          is BookmarksManager.Event.Loaded,
          is BookmarksManager.Event.Created,
          is BookmarksManager.Event.Deleted -> {
            hasAnyBookmarks = bookmarksManager.hasBookmarks()
          }
          is BookmarksManager.Event.Updated -> {
            // no-op
          }
        }
      }
    }
  )

  val miniDrawerWidth = dimensionResource(id = R.dimen.home_screen_mini_drawer_width)

  val miniDrawerDisplayed = mainUiLayoutMode == MainUiLayoutMode.Split &&
    windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
    hasAnyBookmarks

  LaunchedEffect(
    key1 = miniDrawerDisplayed,
    block = { onMiniDrawerDisplayFlagChanged(miniDrawerDisplayed) }
  )

  AnimatedVisibility(
    modifier = Modifier
      .fillMaxHeight()
      .width(miniDrawerWidth),
    visible = miniDrawerDisplayed
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      HomeScreenMiniDrawerLayout()
    }
  }
}

@Composable
private fun ListenForFirewallBypassManagerEvents(
  navigationRouterProvider: () -> NavigationRouter,
  showSiteFirewallBypassController: suspend (FirewallType, HttpUrl, HttpUrl, SiteKey) -> Unit
) {
  val firewallBypassManager: FirewallBypassManager = koinRemember()

  LaunchedEffect(
    key1 = Unit,
    block = {
      firewallBypassManager.showFirewallControllerEvents.collect { showFirewallControllerInfo ->
        val siteFirewallBypassScreen = navigationRouterProvider()
          .getScreenByKey(SiteFirewallBypassScreen.SCREEN_KEY)

        val screenIsAlive = siteFirewallBypassScreen?.screenIsAlive ?: false
        if (screenIsAlive) {
          return@collect
        }

        val firewallType = showFirewallControllerInfo.firewallType
        val urlToOpen = showFirewallControllerInfo.urlToOpen
        val originalRequestUrl = showFirewallControllerInfo.originalRequestUrl
        val siteKey = showFirewallControllerInfo.siteKey
        val onFinished = showFirewallControllerInfo.onFinished

        try {
          showSiteFirewallBypassController(firewallType, urlToOpen, originalRequestUrl, siteKey)
        } finally {
          onFinished.complete(Unit)
          logcat(HomeScreen.TAG) { "showFirewallControllerInfo.onFinished() invoked" }
        }
      }
    }
  )
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