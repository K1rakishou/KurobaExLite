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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.settings.site.SiteSettingsScreen
import com.github.k1rakishou.kurobaexlite.helpers.sort.WeightedSorter
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleSearchToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberPullToRefreshState
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

  // TODO(KurobaEx): use "lastUsedSite" from the settings and update it too (once there are have multiple sites)
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
    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { key ->
          when (key) {
            ToolbarIcons.Search -> {
              kurobaToolbarContainerState.setToolbar(searchToolbar)
            }
            ToolbarIcons.SiteOptions -> {
              val siteSettingsScreen = ComposeScreen.createScreen<SiteSettingsScreen>(
                componentActivity = componentActivity,
                navigationRouter = navigationRouter,
                args = { putParcelable(SiteSettingsScreen.SITE_KEY_ARG, siteKey) }
              )

              navigationRouter.pushScreen(siteSettingsScreen)
            }
            ToolbarIcons.Back -> { onBackPressed() }
            ToolbarIcons.Overflow -> {
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
      windowInsets.copy(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = windowInsets.top + toolbarHeight
      ).asPaddingValues()
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val searchQuery by catalogSelectionScreenViewModel.searchQueryState
    var loadBoardsForSiteEvent by remember { mutableStateOf<AsyncData<List<ChanBoardUiData>>>(AsyncData.Uninitialized) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        catalogSelectionScreenViewModel
          .getOrLoadBoardsForSite(siteKey = siteKey, forceReload = false)
          .collect { boardsListAsync -> loadBoardsForSiteEvent = boardsListAsync }
      }
    )

    GradientBackground(
      modifier = Modifier
        .fillMaxSize()
        .consumeClicks()
    ) {
      val pullToRefreshToPadding = remember(key1 = paddingValues) { paddingValues.calculateTopPadding() }

      PullToRefresh(
        pullToRefreshState = pullToRefreshState,
        topPadding = pullToRefreshToPadding,
        onTriggered = {
          coroutineScope.launch {
            catalogSelectionScreenViewModel
              .getOrLoadBoardsForSite(siteKey = siteKey, forceReload = true)
              .collect { boardsListAsync -> loadBoardsForSiteEvent = boardsListAsync }

            pullToRefreshState.stopRefreshing()
          }
        }
      ) {
        BuildBoardsList(
          searchQuery = searchQuery,
          loadBoardsForSiteEvent = loadBoardsForSiteEvent,
          paddingValues = paddingValues,
          onBoardClicked = { clickedCatalogDescriptor ->
            coroutineScope.launch {
              kurobaToolbarContainerState.popChildToolbars()

              catalogScreenViewModel.loadCatalog(clickedCatalogDescriptor)
              popScreen()
            }
          }
        )
      }
    }
  }

  @Composable
  private fun BuildBoardsList(
    searchQuery: String?,
    loadBoardsForSiteEvent: AsyncData<List<ChanBoardUiData>>,
    paddingValues: PaddingValues,
    onBoardClicked: (CatalogDescriptor) -> Unit
  ) {
    val titleTextSize by globalUiInfoManager.textTitleSizeSp.collectAsState()
    val subtitleTextSize by globalUiInfoManager.textSubTitleSizeSp.collectAsState()
    val defaultHorizPadding = globalUiInfoManager.defaultHorizPadding
    val defaultVertPadding = globalUiInfoManager.defaultVertPadding

    val filteredBoardsAsyncData by produceState(
      initialValue = loadBoardsForSiteEvent,
      key1 = searchQuery,
      key2 = loadBoardsForSiteEvent,
      producer = {
        if (loadBoardsForSiteEvent !is AsyncData.Data || searchQuery.isNullOrEmpty()) {
          value = loadBoardsForSiteEvent
          return@produceState
        }

        val chanBoards = loadBoardsForSiteEvent.data
        val filteredBoards = chanBoards.filter { chanBoardUiData -> chanBoardUiData.matchesQuery(searchQuery) }

        val sortedBoards = WeightedSorter.sort(
          input = filteredBoards,
          query = searchQuery,
          textSelector = { chanBoardUiData -> chanBoardUiData.boardCode }
        )

        value = AsyncData.Data(sortedBoards)
      }
    )

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
                loadBoardsForSiteAsyncData.error.errorMessageOrClassName()
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
          titleTextSize = titleTextSize,
          subtitleTextSize = subtitleTextSize,
          horizPadding = horizPadding,
          vertPadding = vertPadding,
          chanBoardUiData = chanBoard,
          onBoardClicked = onBoardClicked
        )
      })
  }

  @Composable
  private fun BuildChanBoardCell(
    titleTextSize: TextUnit,
    subtitleTextSize: TextUnit,
    horizPadding: Dp,
    vertPadding: Dp,
    chanBoardUiData: ChanBoardUiData,
    onBoardClicked: (CatalogDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
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
      KurobaComposeText(
        text = chanBoardUiData.title,
        color = chanTheme.textColorPrimary,
        fontSize = titleTextSize,
        maxLines = 1
      )

      if (chanBoardUiData.subtitle.isNotNullNorEmpty()) {
        KurobaComposeText(
          text = chanBoardUiData.subtitle,
          color = chanTheme.textColorSecondary,
          fontSize = subtitleTextSize,
          maxLines = 3
        )
      }
    }
  }

  enum class ToolbarIcons {
    Back,
    Search,
    SiteOptions,
    Overflow
  }

  companion object {
    const val CATALOG_DESCRIPTOR_ARG = "catalog_descriptor"

    val SCREEN_KEY = ScreenKey("CatalogSelectionScreen")
  }
}