package com.github.k1rakishou.kurobaexlite.features.boards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.settings.site.SiteSettingsScreen
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.sort.WeightedSorter
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleSearchToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefreshState
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class CatalogSelectionScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<KurobaChildToolbar>(screenArgs, componentActivity, navigationRouter) {
  private val catalogSelectionScreenViewModel: CatalogSelectionScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false

  private val catalogDescriptor: CatalogDescriptor? by argumentOrNullLazy(CATALOG_DESCRIPTOR_ARG)

  private val siteKey: SiteKey
    get() = catalogDescriptor?.siteKey ?: Chan4.SITE_KEY

  private val defaultToolbarKey = "${screenKey.key}_default"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"
  private val searchToolbarKey = "${screenKey.key}_search"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .titleId(R.string.board_selection_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Search, drawableId = R.drawable.ic_baseline_search_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Refresh, drawableId = R.drawable.ic_baseline_refresh_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.SiteOptions, drawableId = R.drawable.ic_baseline_settings_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  override val defaultToolbar: KurobaChildToolbar by lazy {
    SimpleToolbar(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  private val searchToolbar: KurobaChildToolbar by lazy {
    SimpleSearchToolbar(
      initialSearchQuery = null,
      toolbarKey = searchToolbarKey,
      onSearchQueryUpdated = { searchQuery -> catalogSelectionScreenViewModel.updateSearchQuery(searchQuery) },
      closeSearch = { kurobaToolbarContainerState.popToolbar(searchToolbarKey) }
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<KurobaChildToolbar>(screenKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val coroutineScope = rememberCoroutineScope()

    ToolbarInternal(
      defaultToolbarState = defaultToolbarState,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      screenKey = screenKey,
      switchToSearchToolbar = {
        kurobaToolbarContainerState.setToolbar(searchToolbar)
      },
      refreshSiteBoardInfo = {
        coroutineScope.launch {
          catalogSelectionScreenViewModel.getOrLoadBoardsForSite(
            siteKey = siteKey,
            forceReload = true
          )
        }
      },
      showSiteSettingsScreen = {
        val siteSettingsScreen = ComposeScreen.createScreen<SiteSettingsScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          args = { putParcelable(SiteSettingsScreen.SITE_KEY_ARG, siteKey) }
        )

        navigationRouter.pushScreen(siteSettingsScreen)
      },
      onBackPressed = { coroutineScope.launch { onBackPressed() } }
    )
  }

  override val screenContentLoadedFlow: StateFlow<Boolean> by lazy { MutableStateFlow(true) }

  @Composable
  override fun HomeNavigationScreenContent() {
    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        snackbarManager.popSnackbar(SnackbarId.ReloadLastVisitedCatalog)
        snackbarManager.popSnackbar(SnackbarId.ReloadLastVisitedThread)
      }
    )

    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copyInsets(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = windowInsets.top + toolbarHeight
      ).asPaddingValues()
    }

    val pullToRefreshState = rememberPullToRefreshState()

    GradientBackground(
      modifier = Modifier
        .fillMaxSize()
        .consumeClicks()
    ) {
      KurobaComposeFadeIn {
        ContentInternal(
          catalogDescriptor = catalogDescriptor,
          paddingValues = paddingValues,
          pullToRefreshState = pullToRefreshState,
          siteKeyProvider = { siteKey },
          popScreen = { popScreen() }
        )
      }
    }
  }

  enum class ToolbarIcons {
    Back,
    Search,
    Refresh,
    SiteOptions,
    Overflow
  }

  companion object {
    const val CATALOG_DESCRIPTOR_ARG = "catalog_descriptor"

    val SCREEN_KEY = ScreenKey("CatalogSelectionScreen")
  }
}

@Composable
private fun ToolbarInternal(
  defaultToolbarState: SimpleToolbarState<CatalogSelectionScreen.ToolbarIcons>,
  kurobaToolbarContainerState: KurobaToolbarContainerState<KurobaChildToolbar>,
  screenKey: ScreenKey,
  showSiteSettingsScreen: () -> Unit,
  switchToSearchToolbar: () -> Unit,
  refreshSiteBoardInfo: () -> Unit,
  onBackPressed: () -> Unit
) {
  LaunchedEffect(
    key1 = Unit,
    block = {
      defaultToolbarState.iconClickEvents.collect { key ->
        when (key) {
          CatalogSelectionScreen.ToolbarIcons.Search -> {
            switchToSearchToolbar()
          }
          CatalogSelectionScreen.ToolbarIcons.Refresh -> {
            refreshSiteBoardInfo()
          }
          CatalogSelectionScreen.ToolbarIcons.SiteOptions -> {
            showSiteSettingsScreen()
          }
          CatalogSelectionScreen.ToolbarIcons.Back -> {
            onBackPressed()
          }
          CatalogSelectionScreen.ToolbarIcons.Overflow -> {
            // no-op
          }
        }
      }
    }
  )

  KurobaToolbarContainer(
    toolbarContainerKey = screenKey.key,
    kurobaToolbarContainerState = kurobaToolbarContainerState,
    canProcessBackEvent = { true }
  )
}

@Composable
private fun ContentInternal(
  catalogDescriptor: CatalogDescriptor?,
  paddingValues: PaddingValues,
  pullToRefreshState: PullToRefreshState,
  siteKeyProvider: () -> SiteKey,
  popScreen: () -> Unit
) {
  val coroutineScope = rememberCoroutineScope()

  val catalogScreenViewModel: CatalogScreenViewModel = koinRememberViewModel()
  val catalogSelectionScreenViewModel: CatalogSelectionScreenViewModel = koinRememberViewModel()

  val searchQuery by catalogSelectionScreenViewModel.searchQueryState
  val loadedBoardsForSite by catalogSelectionScreenViewModel.loadedBoardsForSite

  LaunchedEffect(
    key1 = Unit,
    block = {
      catalogSelectionScreenViewModel.getOrLoadBoardsForSite(
        siteKey = siteKeyProvider(),
        forceReload = false
      )
    }
  )

  val pullToRefreshToPadding = remember(key1 = paddingValues) { paddingValues.calculateTopPadding() }

  PullToRefresh(
    pullToRefreshState = pullToRefreshState,
    topPadding = pullToRefreshToPadding,
    onTriggered = {
      coroutineScope.launch {
        catalogSelectionScreenViewModel.getOrLoadBoardsForSite(
          siteKey = siteKeyProvider(),
          forceReload = true,
          hidePullToRefreshIndicator = { pullToRefreshState.stopRefreshing() }
        )
      }
    }
  ) {
    BuildBoardsList(
      catalogDescriptor = catalogDescriptor,
      searchQuery = searchQuery,
      loadedBoardsForSite = loadedBoardsForSite,
      paddingValues = paddingValues,
      onBoardClicked = { clickedCatalogDescriptor ->
        coroutineScope.launch {
          catalogScreenViewModel.loadCatalog(clickedCatalogDescriptor)
          popScreen()
        }
      }
    )
  }
}

@Composable
private fun BuildBoardsList(
  catalogDescriptor: CatalogDescriptor?,
  searchQuery: String?,
  loadedBoardsForSite: AsyncData<List<ChanBoardUiData>>,
  paddingValues: PaddingValues,
  onBoardClicked: (CatalogDescriptor) -> Unit
) {
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val titleTextSize by globalUiInfoManager.textTitleSizeSp.collectAsState()
  val subtitleTextSize by globalUiInfoManager.textSubTitleSizeSp.collectAsState()
  val defaultHorizPadding = remember { globalUiInfoManager.defaultHorizPadding }
  val defaultVertPadding = remember { globalUiInfoManager.defaultVertPadding }

  val loadedBoardsForSiteUpdated by rememberUpdatedState(newValue = loadedBoardsForSite)
  val searchQueryUpdated by rememberUpdatedState(newValue = searchQuery)
  var filteredBoardsAsyncData by remember { mutableStateOf(loadedBoardsForSite) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      combine(
        flow = snapshotFlow { loadedBoardsForSiteUpdated },
        flow2 = snapshotFlow { searchQueryUpdated },
        transform = { a, b -> a to b }
      ).collectLatest { (chanBoardUiAsyncData, query) ->
        if (chanBoardUiAsyncData !is AsyncData.Data || query.isNullOrEmpty()) {
          filteredBoardsAsyncData = chanBoardUiAsyncData
          return@collectLatest
        }

        delay(250L)

        val chanBoards = chanBoardUiAsyncData.data
        val filteredBoards = chanBoards.filter { chanBoardUiData -> chanBoardUiData.matchesQuery(query) }
        val sortedBoards = WeightedSorter.sort(
          input = filteredBoards,
          query = query,
          textSelector = { chanBoardUiData -> chanBoardUiData.boardCode }
        )

        filteredBoardsAsyncData = AsyncData.Data(sortedBoards)
      }
    })

  val lazyListState = rememberLazyListState()

  LaunchedEffect(
    key1 = searchQuery,
    block = {
      awaitFrame()

      if (lazyListState.firstVisibleItemIndex <= 0) {
        return@LaunchedEffect
      }

      lazyListState.scrollToItem(0)
    }
  )

  LazyColumnWithFastScroller(
    lazyListContainerModifier = Modifier.fillMaxSize(),
    contentPadding = paddingValues,
    lazyListState = lazyListState,
    content = {
      when (val loadBoardsForSiteAsyncData = filteredBoardsAsyncData) {
        AsyncData.Uninitialized -> {
          // no-op
        }
        AsyncData.Loading -> {
          item(key = "loading_indicator") {
            KurobaComposeLoadingIndicator(
              modifier = Modifier
                .fillParentMaxSize()
                .padding(8.dp)
            )
          }
        }
        is AsyncData.Error -> {
          item(key = "error_indicator") {
            val errorMessage = remember(key1 = loadBoardsForSiteAsyncData) {
              loadBoardsForSiteAsyncData.error.errorMessageOrClassName(userReadable = true)
            }

            KurobaComposeText(
              modifier = Modifier
                .fillParentMaxSize()
                .padding(8.dp),
              text = errorMessage
            )
          }
        }
        is AsyncData.Data -> {
          val siteBoards = loadBoardsForSiteAsyncData.data
          if (siteBoards.isEmpty()) {
            if (searchQuery == null) {
              item(key = "no_boards_indicator") {
                KurobaComposeText(
                  modifier = Modifier
                    .fillParentMaxSize()
                    .padding(8.dp),
                  text = stringResource(id = R.string.board_selection_screen_no_boards_loaded)
                )
              }
            } else {
              item(key = "nothing_found_indicator") {
                KurobaComposeText(
                  modifier = Modifier
                    .fillParentMaxSize()
                    .padding(8.dp),
                  text = stringResource(
                    id = R.string.board_selection_screen_nothing_found_by_query,
                    searchQuery
                  )
                )
              }
            }
          } else {
            buildChanBoardsList(
              catalogDescriptor = catalogDescriptor,
              searchQuery = searchQuery,
              titleTextSize = titleTextSize,
              subtitleTextSize = subtitleTextSize,
              horizPadding = defaultHorizPadding,
              vertPadding = defaultVertPadding,
              chanBoardUiDataList = siteBoards,
              onBoardClicked = onBoardClicked
            )
          }
        }
      }
    })
}

private fun LazyListScope.buildChanBoardsList(
  catalogDescriptor: CatalogDescriptor?,
  searchQuery: String?,
  titleTextSize: TextUnit,
  subtitleTextSize: TextUnit,
  horizPadding: Dp,
  vertPadding: Dp,
  chanBoardUiDataList: List<ChanBoardUiData>,
  onBoardClicked: (CatalogDescriptor) -> Unit
) {
  items(
    count = chanBoardUiDataList.size,
    key = { index -> chanBoardUiDataList[index].catalogDescriptor },
    itemContent = { index ->
      val chanBoard = chanBoardUiDataList[index]

      BuildChanBoardCell(
        catalogDescriptor = catalogDescriptor,
        searchQuery = searchQuery,
        titleTextSize = titleTextSize,
        subtitleTextSize = subtitleTextSize,
        horizPadding = horizPadding,
        vertPadding = vertPadding,
        chanBoardUiData = chanBoard,
        onBoardClicked = onBoardClicked
      )
    }
  )
}

@Composable
private fun BuildChanBoardCell(
  catalogDescriptor: CatalogDescriptor?,
  searchQuery: String?,
  titleTextSize: TextUnit,
  subtitleTextSize: TextUnit,
  horizPadding: Dp,
  vertPadding: Dp,
  chanBoardUiData: ChanBoardUiData,
  onBoardClicked: (CatalogDescriptor) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val postCommentApplier: PostCommentApplier = koinRemember()

  val bgColorWithAlpha = remember(key1 = chanTheme.highlighterColor) {
    chanTheme.highlighterColor.copy(alpha = 0.3f)
  }

  val backgroundColorModifier = if (chanBoardUiData.catalogDescriptor == catalogDescriptor) {
    Modifier.background(bgColorWithAlpha)
  } else {
    Modifier
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .then(backgroundColorModifier)
      .kurobaClickable(onClick = { onBoardClicked(chanBoardUiData.catalogDescriptor) })
      .padding(horizontal = horizPadding, vertical = vertPadding)
  ) {
    kotlin.run {
      val textFormatted = remember(key1 = searchQuery, key2 = chanTheme) {
        return@remember buildAnnotatedString {
          val titleFormatted = buildAnnotatedString {
            withStyle(SpanStyle(color = chanTheme.textColorPrimary)) { append(chanBoardUiData.title) }
          }

          if (searchQuery != null && searchQuery.isNotEmpty()) {
            val (_, titleFormattedWithSearchQuery) = postCommentApplier.markOrUnmarkSearchQuery(
              chanTheme = chanTheme,
              searchQuery = searchQuery,
              minQueryLength = 2,
              string = titleFormatted
            )

            append(titleFormattedWithSearchQuery)
          } else {
            append(titleFormatted)
          }
        }
      }

      KurobaComposeText(
        text = textFormatted,
        fontSize = titleTextSize,
        maxLines = 1
      )
    }

    if (chanBoardUiData.subtitle.isNotNullNorEmpty()) {
      kotlin.run {
        val textFormatted = remember(key1 = searchQuery, key2 = chanTheme) {
          return@remember buildAnnotatedString {
            val subtitleFormatted = buildAnnotatedString {
              withStyle(SpanStyle(color = chanTheme.textColorSecondary)) { append(chanBoardUiData.subtitle) }
            }

            if (searchQuery != null && searchQuery.isNotEmpty()) {
              val (_, subtitleFormattedWithSearchQuery) = postCommentApplier.markOrUnmarkSearchQuery(
                chanTheme = chanTheme,
                searchQuery = searchQuery,
                minQueryLength = 2,
                string = subtitleFormatted
              )

              append(subtitleFormattedWithSearchQuery)
            } else {
              append(subtitleFormatted)
            }
          }
        }

        KurobaComposeText(
          text = textFormatted,
          fontSize = subtitleTextSize,
          maxLines = 3
        )
      }
    }
  }
}