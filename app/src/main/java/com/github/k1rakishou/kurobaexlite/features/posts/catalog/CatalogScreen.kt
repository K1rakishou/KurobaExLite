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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.boards.BoardSelectionScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.sort.SortCatalogThreadsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.IReplyLayoutState
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutContainer
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutVisibility
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.LeftIconInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.MiddlePartInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.PostScreenToolbarInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.RightPartInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val catalogScreenToolbarActionHandler by lazy {
    CatalogScreenToolbarActionHandler(componentActivity)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter)
  }

  private val replyLayoutState: IReplyLayoutState
    get() = replyLayoutViewModel.getOrCreateReplyLayoutState(catalogScreenViewModel.chanDescriptor)

  private val replyLayoutToolbarState = KurobaToolbarState(
    leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_close_24),
    middlePartInfo = MiddlePartInfo(centerContent = false),
    rightPartInfo = RightPartInfo(
      ToolbarIcon(ReplyToolbarIcons.PickLocalFile, R.drawable.ic_baseline_attach_file_24)
    )
  )

  private val catalogToolbarState = KurobaToolbarState(
    leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_dehaze_24),
    middlePartInfo = MiddlePartInfo(centerContent = true),
    rightPartInfo = RightPartInfo(
      ToolbarIcon(CatalogToolbarIcons.Search, R.drawable.ic_baseline_search_24, false),
      ToolbarIcon(CatalogToolbarIcons.Sort, R.drawable.ic_baseline_sort_24, false),
      ToolbarIcon(CatalogToolbarIcons.Overflow, R.drawable.ic_baseline_more_vert_24),
    ),
    postScreenToolbarInfo = PostScreenToolbarInfo(isCatalogScreen = true)
  )

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
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_BOOKMARKS_SCREEN_POSITION,
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_bookmarks_on_left_side),
        isChecked = { appSettings.bookmarksScreenOnLeftSide.read() }
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
    val mainUiLayoutModeMut by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut ?: return

    val catalogDescriptor by catalogScreenViewModel.currentlyOpenedCatalogFlow.collectAsState()
    val replyLayoutVisibility by replyLayoutStateByDescriptor(catalogDescriptor).replyLayoutVisibilityState

    if (replyLayoutVisibility != ReplyLayoutVisibility.Closed) {
      ReplyLayoutToolbar(mainUiLayoutMode)
    } else {
      CatalogScreenToolbar(mainUiLayoutMode)
    }
  }

  @Composable
  private fun ReplyLayoutToolbar(mainUiLayoutMode: MainUiLayoutMode) {
    val context = LocalContext.current

    val currentCatalogDescriptorMut by threadScreenViewModel.currentlyOpenedCatalogFlow.collectAsState()
    val currentCatalogDescriptor = currentCatalogDescriptorMut

    LaunchedEffect(
      key1 = currentCatalogDescriptor,
      block = {
        if (currentCatalogDescriptor == null) {
          return@LaunchedEffect
        }

        replyLayoutToolbarState.toolbarTitleState.value = context.resources.getString(
          R.string.catalog_new_thread_on_board,
          currentCatalogDescriptor.boardCode
        )
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        replyLayoutToolbarState.toolbarIconClickEventFlow.collect { key ->
          when (key as ReplyToolbarIcons) {
            ReplyToolbarIcons.PickLocalFile -> {
              val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
                ?: return@collect

              replyLayoutViewModel.onPickFileRequested(catalogDescriptor)
            }
          }
        }
      }
    )

    KurobaToolbar(
      screenKey = screenKey,
      kurobaToolbarState = replyLayoutToolbarState,
      canProcessBackEvent = { canProcessBackEvent(mainUiLayoutMode, globalUiInfoManager.currentPage()) },
      onLeftIconClicked = { replyLayoutState.onBackPressed() },
      onMiddleMenuClicked = null,
      onSearchQueryUpdated = null
    )
  }

  @Composable
  private fun CatalogScreenToolbar(
    mainUiLayoutMode: MainUiLayoutMode
  ) {
    val screenContentLoaded by screenContentLoadedFlow.collectAsState()

    LaunchedEffect(
      key1 = screenContentLoaded,
      block = {
        catalogToolbarState.rightPartInfo?.let { rightPartInfo ->
          rightPartInfo.toolbarIcons.forEach { toolbarIcon ->
            if (toolbarIcon.key == CatalogToolbarIcons.Overflow) {
              return@forEach
            }

            toolbarIcon.iconVisible.value = screenContentLoaded
          }
        }
      }
    )

    UpdateToolbarTitle(
      parsedPostDataCache = parsedPostDataCache,
      postScreenState = catalogScreenViewModel.postScreenState,
      kurobaToolbarState = catalogToolbarState
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        catalogToolbarState.toolbarIconClickEventFlow.collect { key ->
          when (key as CatalogToolbarIcons) {
            CatalogToolbarIcons.Search -> catalogToolbarState.openSearch()
            CatalogToolbarIcons.Sort -> {
              val sortCatalogThreadsScreen = SortCatalogThreadsScreen(
                componentActivity = componentActivity,
                navigationRouter = navigationRouter,
                onApplied = { catalogScreenViewModel.onCatalogSortChanged() }
              )

              navigationRouter.presentScreen(sortCatalogThreadsScreen)
            }
            CatalogToolbarIcons.Overflow -> {
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
            }
          }
        }
      }
    )

    KurobaToolbar(
      screenKey = screenKey,
      kurobaToolbarState = catalogToolbarState,
      canProcessBackEvent = { canProcessBackEvent(mainUiLayoutMode, globalUiInfoManager.currentPage()) },
      onLeftIconClicked = { globalUiInfoManager.openDrawer() },
      onMiddleMenuClicked = {
        val boardSelectionScreen = BoardSelectionScreen(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          catalogDescriptor = catalogScreenViewModel.chanDescriptor as? CatalogDescriptor
        )

        navigationRouter.pushScreen(boardSelectionScreen)
      },
      onSearchQueryUpdated = { searchQuery ->
        globalUiInfoManager.onChildScreenSearchStateChanged(screenKey, searchQuery)
        catalogScreenViewModel.updateSearchQuery(searchQuery)
      }
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    HandleBackPresses {
      if (replyLayoutState.onBackPressed()) {
        return@HandleBackPresses true
      }

      if (catalogToolbarState.onBackPressed()) {
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
        globalUiInfoManager.onChildContentScrolling(screenKey, delta)
      },
      onPostListTouchingTopOrBottomStateChanged = { touching ->
        globalUiInfoManager.onPostListTouchingTopOrBottomStateChanged(screenKey, touching)
      },
      onPostListDragStateChanged = { dragging ->
        globalUiInfoManager.onPostListDragStateChanged(screenKey, dragging)
      },
      onFastScrollerDragStateChanged = { dragging ->
        globalUiInfoManager.onFastScrollerDragStateChanged(screenKey, dragging)
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

  @Composable
  private fun replyLayoutStateByDescriptor(chanDescriptor: ChanDescriptor?): IReplyLayoutState {
    return remember(key1 = chanDescriptor) {
      replyLayoutViewModel.getOrCreateReplyLayoutState(chanDescriptor)
    }
  }

  private enum class ReplyToolbarIcons {
    PickLocalFile
  }

  private enum class CatalogToolbarIcons {
    Search,
    Sort,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("CatalogScreen")
  }

}