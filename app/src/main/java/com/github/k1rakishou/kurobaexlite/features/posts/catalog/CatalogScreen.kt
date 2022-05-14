package com.github.k1rakishou.kurobaexlite.features.posts.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.boards.CatalogSelectionScreen
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.navigation.HistoryScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.toolbar.CatalogScreenDefaultToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.toolbar.CatalogScreenReplyToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenLocalSearchToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.sort.SortCatalogThreadsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.IReplyLayoutState
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutContainer
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutVisibility
import com.github.k1rakishou.kurobaexlite.helpers.forceInit
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class CatalogScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val replyLayoutViewModel: ReplyLayoutViewModel by componentActivity.viewModel()
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)
  private val chanThreadManager: ChanThreadManager by inject(ChanThreadManager::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val historyScreenViewModel = componentActivity.viewModel<HistoryScreenViewModel>()
  private val bookmarksScreenViewModel = componentActivity.viewModel<BookmarksScreenViewModel>()

  private val catalogScreenToolbarActionHandler by lazy {
    CatalogScreenToolbarActionHandler(componentActivity)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter)
  }

  private val replyLayoutState: IReplyLayoutState
    get() = replyLayoutViewModel.getOrCreateReplyLayoutState(catalogScreenViewModel.chanDescriptor)

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<KurobaChildToolbar>(screenKey)
  }

  private val defaultToolbar: KurobaChildToolbar by lazy {
    CatalogScreenDefaultToolbar(
      catalogScreenViewModel = catalogScreenViewModel,
      parsedPostDataCache = parsedPostDataCache,
      onBackPressed = { globalUiInfoManager.openDrawer() },
      showCatalogSelectionScreen = {
        val catalogSelectionScreen = CatalogSelectionScreen(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          catalogDescriptor = catalogScreenViewModel.chanDescriptor as? CatalogDescriptor
        )
        navigationRouter.pushScreen(catalogSelectionScreen)
      },
      showSortCatalogThreadsScreen = {
        val sortCatalogThreadsScreen = SortCatalogThreadsScreen(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          onApplied = { catalogScreenViewModel.onCatalogSortChanged() }
        )

        navigationRouter.presentScreen(sortCatalogThreadsScreen)
      },
      showOverflowMenu = {
        navigationRouter.presentScreen(
          FloatingMenuScreen(
            floatingMenuKey = FloatingMenuScreen.CATALOG_OVERFLOW,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            menuItems = floatingMenuItems,
            onMenuItemClicked = { menuItem ->
              catalogScreenToolbarActionHandler.processClickedToolbarMenuItem(
                navigationRouter = navigationRouter,
                menuItem = menuItem
              )
            }
          )
        )
      },
      showLocalSearchToolbar = {
        kurobaToolbarContainerState.fadeInToolbar(localSearchToolbar)
      }
    )
  }

  private val replyToolbar: KurobaChildToolbar by lazy {
    CatalogScreenReplyToolbar(
      threadScreenViewModel = threadScreenViewModel,
      closeReplyLayout = { replyLayoutState.onBackPressed() },
      pickLocalFile = {
        val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
          ?: return@CatalogScreenReplyToolbar

        replyLayoutViewModel.onPickFileRequested(catalogDescriptor)
      }
    )
  }

  private val localSearchToolbar: KurobaChildToolbar by lazy {
    PostsScreenLocalSearchToolbar(
      screenKey = screenKey,
      onToolbarCreated = { globalUiInfoManager.onChildScreenSearchStateChanged(screenKey, true) },
      onToolbarDisposed = { globalUiInfoManager.onChildScreenSearchStateChanged(screenKey, false) },
      onSearchQueryUpdated = { searchQuery -> catalogScreenViewModel.updateSearchQuery(searchQuery) },
      closeSearch = { toolbarKey -> kurobaToolbarContainerState.popToolbar(toolbarKey) }
    )
  }

  private val floatingMenuItems: List<FloatingMenuItem> by lazy {
    listOf(
      FloatingMenuItem.Text(
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_RELOAD,
        text = FloatingMenuItem.MenuItemText.Id(R.string.reload)
      ),
      FloatingMenuItem.Text(
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_LAYOUT_MODE,
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_layout_mode)
      ),
      FloatingMenuItem.Check(
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_HISTORY_SCREEN_POSITION,
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_history_on_left_side),
        isChecked = { appSettings.historyScreenOnLeftSide.read() }
      ),
      FloatingMenuItem.Text(
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_OPEN_THREAD_BY_IDENTIFIER,
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_open_thread_by_identifier),
        subText = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_open_thread_by_identifier_subtitle)
      ),
      FloatingMenuItem.Text(
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_CATALOG_ALBUM,
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_album),
      ),
      FloatingMenuItem.Footer(
        items = listOf(
          FloatingMenuItem.Icon(
            menuItemKey = CatalogScreenToolbarActionHandler.ACTION_SCROLL_TOP,
            iconId = R.drawable.ic_baseline_arrow_upward_24
          ),
          FloatingMenuItem.Icon(
            menuItemKey = CatalogScreenToolbarActionHandler.ACTION_SCROLL_BOTTOM,
            iconId = R.drawable.ic_baseline_arrow_downward_24
          )
        )
      )
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = true
  override val screenContentLoadedFlow: StateFlow<Boolean> = catalogScreenViewModel.postScreenState.contentLoaded

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    KurobaToolbarContainer(
      toolbarContainerKey = screenKey.key,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = {
        val mainUiLayoutMode = globalUiInfoManager.currentUiLayoutModeState.value
          ?: return@KurobaToolbarContainer false
        val currentPage = globalUiInfoManager.currentPage()
          ?: return@KurobaToolbarContainer false

        return@KurobaToolbarContainer when (mainUiLayoutMode) {
          MainUiLayoutMode.Phone -> {
            currentPage.screenKey == screenKey
          }
          MainUiLayoutMode.Split -> {
            currentPage.screenKey == screenKey || currentPage.screenKey == ThreadScreen.SCREEN_KEY
          }
        }
      },
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    HandleBackPresses {
      if (replyLayoutState.onBackPressed()) {
        return@HandleBackPresses true
      }

      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      for (composeScreen in navigationRouter.navigationScreensStack.asReversed()) {
        if (composeScreen.onBackPressed()) {
          return@HandleBackPresses true
        }
      }

      return@HandleBackPresses false
    }

    ProcessCaptchaRequestEvents(
      homeScreenViewModel = homeScreenViewModel,
      currentChanDescriptor = { catalogScreenViewModel.catalogDescriptor }
    )

    Box(modifier = Modifier.fillMaxSize()) {
      CatalogPostListScreen()
    }
  }

  @Composable
  private fun BoxScope.CatalogPostListScreen() {
    val windowInsets = LocalWindowInsets.current

    val orientationMut by globalUiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val mainUiLayoutModeMut by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut ?: return

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val fabSize = dimensionResource(id = R.dimen.fab_size)
    val fabVertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)
    val replyLayoutContainerOpenedHeight = dimensionResource(id = R.dimen.reply_layout_container_opened_height)

    val kurobaSnackbarState = rememberKurobaSnackbarState()
    val postCellCommentTextSizeSp by globalUiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by globalUiInfoManager.postCellSubjectTextSizeSp.collectAsState()
    val replyLayoutVisibilityInfoStateForScreen by globalUiInfoManager.replyLayoutVisibilityInfoStateForScreen(screenKey)

    val postListOptions by remember(key1 = windowInsets, key2 = replyLayoutVisibilityInfoStateForScreen) {
      derivedStateOf {
        val bottomPadding = when (replyLayoutVisibilityInfoStateForScreen) {
          ReplyLayoutVisibility.Closed -> windowInsets.bottom
          ReplyLayoutVisibility.Opened,
          ReplyLayoutVisibility.Expanded -> windowInsets.bottom + replyLayoutContainerOpenedHeight
        }

        PostListOptions(
          isCatalogMode = isCatalogScreen,
          isInPopup = false,
          pullToRefreshEnabled = true,
          ownerScreenKey = screenKey,
          contentPadding = PaddingValues(
            top = toolbarHeight + windowInsets.top,
            bottom = bottomPadding + fabSize + fabVertOffset
          ),
          mainUiLayoutMode = mainUiLayoutMode,
          postCellCommentTextSizeSp = postCellCommentTextSizeSp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
          detectLinkableClicks = true,
          orientation = orientation
        )
      }
    }

    val coroutineScope = rememberCoroutineScope()

    PostListContent(
      modifier = Modifier.fillMaxSize(),
      postListOptions = postListOptions,
      postsScreenViewModel = catalogScreenViewModel,
      onPostCellClicked = { postCellData ->
        globalUiInfoManager.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)

        val threadDescriptor = ThreadDescriptor(
          catalogDescriptor = postCellData.postDescriptor.catalogDescriptor,
          threadNo = postCellData.postNo
        )

        threadScreenViewModel.loadThread(threadDescriptor)
      },
      onPostCellLongClicked = { postCellData ->
        coroutineScope.launch {
          postLongtapContentMenu.showMenu(
            coroutineScope = coroutineScope,
            postListOptions = postListOptions,
            postCellData = postCellData,
            reparsePostsFunc = { postDescriptors ->
              catalogScreenViewModel.reparsePostsByDescriptors(postDescriptors)
            }
          )
        }
      },
      onLinkableClicked = { postCellData, linkable ->
        // no-op (for now?)
      },
      onLinkableLongClicked = { postCellData, linkable ->
        // no-op (for now?)
      },
      onPostRepliesClicked = { postDescriptor ->
        // no-op
      },
      onQuotePostClicked = { postCellData ->
        // no-op
      },
      onQuotePostWithCommentClicked = { postCellData ->
        // no-op
      },
      onPostImageClicked = { chanDescriptor, postImageData, thumbnailBoundsInRoot ->
        val catalogDescriptor = chanDescriptor as CatalogDescriptor
        clickedThumbnailBoundsStorage.storeBounds(postImageData, thumbnailBoundsInRoot)

        val mediaViewerScreen = MediaViewerScreen(
          mediaViewerParams = MediaViewerParams.Catalog(
            catalogDescriptor = catalogDescriptor,
            initialImageUrl = postImageData.fullImageUrl.toHttpUrl()
          ),
          openedFromScreen = screenKey,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter
        )

        navigationRouter.presentScreen(mediaViewerScreen)
      },
      onPostListScrolled = { delta ->
        globalUiInfoManager.onContentListScrolling(screenKey, delta)
      },
      onPostListTouchingTopOrBottomStateChanged = { touching ->
        globalUiInfoManager.onContentListTouchingTopOrBottomStateChanged(screenKey, touching)
      },
      onCurrentlyTouchingPostList = { touching ->
        globalUiInfoManager.onCurrentlyTouchingContentList(screenKey, touching)
      },
      onFastScrollerDragStateChanged = { dragging ->
        globalUiInfoManager.onFastScrollerDragStateChanged(screenKey, dragging)
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = { kurobaToolbarContainerState.setToolbar(defaultToolbar) }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        combine(
          flow = chanThreadManager.currentlyOpenedCatalogFlow,
          flow2 = chanThreadManager.currentlyOpenedThreadFlow,
          transform = { catalog, thread -> catalog to thread }
        )
          .takeWhile { (catalog, thread) -> catalog == null || thread == null }
          .filter { (catalog, thread) -> catalog != null || thread != null }
          .collect {
            // Force init the view models after a catalog or thread is loaded to make sure they start
            // processing events from everywhere round
            historyScreenViewModel.forceInit()
            bookmarksScreenViewModel.forceInit()
          }
      }
    )

    val currentCatalogDescriptor by threadScreenViewModel.currentlyOpenedCatalogFlow.collectAsState()
    val replyLayoutVisibility by replyLayoutViewModel.getOrCreateReplyLayoutState(currentCatalogDescriptor)
      .replyLayoutVisibilityState

    LaunchedEffect(
      key1 = replyLayoutVisibility,
      block = {
        when (replyLayoutVisibility) {
          ReplyLayoutVisibility.Closed -> {
            kurobaToolbarContainerState.popToolbar(replyToolbar.toolbarKey)
          }
          ReplyLayoutVisibility.Opened,
          ReplyLayoutVisibility.Expanded -> {
            kurobaToolbarContainerState.fadeInToolbar(replyToolbar)
          }
        }
      }
    )

    if (mainUiLayoutMode == MainUiLayoutMode.Split) {
      PostsScreenFloatingActionButton(
        screenKey = screenKey,
        screenContentLoadedFlow = screenContentLoadedFlow,
        mainUiLayoutMode = mainUiLayoutMode,
        onFabClicked = { clickedFabScreenKey ->
          if (screenKey != clickedFabScreenKey) {
            return@PostsScreenFloatingActionButton
          }

          replyLayoutState.openReplyLayout()
        }
      )
    } else {
      LaunchedEffect(
        key1 = Unit,
        block = {
          homeScreenViewModel.homeScreenFabClickEventFlow.collectLatest { clickedFabScreenKey ->
            if (screenKey != clickedFabScreenKey) {
              return@collectLatest
            }

            replyLayoutState.openReplyLayout()
          }
        }
      )
    }

    ReplyLayoutContainer(
      chanDescriptor = catalogScreenViewModel.catalogDescriptor,
      replyLayoutState = replyLayoutState,
      replyLayoutViewModel = replyLayoutViewModel,
      onAttachedMediaClicked = { attachedMedia ->
        // TODO(KurobaEx): show options
      },
      onPostedSuccessfully = { postDescriptor ->
        threadScreenViewModel.loadThread(postDescriptor.threadDescriptor)
        globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
      }
    )

    KurobaSnackbarContainer(
      modifier = Modifier.fillMaxSize(),
      screenKey = screenKey,
      isTablet = globalUiInfoManager.isTablet,
      kurobaSnackbarState = kurobaSnackbarState
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("CatalogScreen")
  }

}