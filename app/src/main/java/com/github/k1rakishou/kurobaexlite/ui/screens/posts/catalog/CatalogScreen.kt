package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.LeftIconInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.MiddlePartInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.PostScreenToolbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.boards.BoardSelectionScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.home.LocalMainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.ui.screens.main.MainScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.sort.SortCatalogThreadsScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import kotlinx.coroutines.flow.StateFlow
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
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)

  private val catalogScreenToolbarActionHandler by lazy {
    CatalogScreenToolbarActionHandler()
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
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_BOOKMARKS_SCREEN_POSITION,
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_bookmarks_on_left_side),
        isChecked = { appSettings.bookmarksScreenOnLeftSide.read() }
      ),
      FloatingMenuItem.Text(
        menuItemKey = CatalogScreenToolbarActionHandler.ACTION_OPEN_THREAD_BY_IDENTIFIER,
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_open_thread_by_identifier),
        subText = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_open_thread_by_identifier_subtitle)
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
    val mainUiLayoutMode = LocalMainUiLayoutMode.current

    val kurobaToolbarState = remember(key1 = mainUiLayoutMode) {
      return@remember KurobaToolbarState(
        leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_dehaze_24),
        middlePartInfo = MiddlePartInfo(centerContent = true),
        postScreenToolbarInfo = PostScreenToolbarInfo(isCatalogScreen = true)
      )
    }

    UpdateToolbarTitle(
      parsedPostDataCache = parsedPostDataCache,
      postScreenState = catalogScreenViewModel.postScreenState,
      kurobaToolbarState = kurobaToolbarState
    )

    KurobaToolbar(
      screenKey = screenKey,
      componentActivity = componentActivity,
      kurobaToolbarState = kurobaToolbarState,
      navigationRouter = navigationRouter,
      canProcessBackEvent = { canProcessBackEvent(mainUiLayoutMode, uiInfoManager.currentPage) },
      onLeftIconClicked = { uiInfoManager.openDrawer() },
      onMiddleMenuClicked = {
        val mainScreenRouter = navigationRouter.getRouterByKey(MainScreen.SCREEN_KEY.key)
        val boardSelectionScreen = BoardSelectionScreen(
          componentActivity = componentActivity,
          navigationRouter = mainScreenRouter,
          catalogDescriptor = catalogScreenViewModel.chanDescriptor as? CatalogDescriptor
        )

        mainScreenRouter.pushScreen(boardSelectionScreen)
      },
      onSearchQueryUpdated = { searchQuery ->
        uiInfoManager.onChildScreenSearchStateChanged(screenKey, searchQuery)
        catalogScreenViewModel.updateSearchQuery(searchQuery)
      },
      onToolbarSortClicked = {
        val sortCatalogThreadsScreen = SortCatalogThreadsScreen(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          onApplied = { catalogScreenViewModel.onCatalogSortChanged() }
        )

        navigationRouter.presentScreen(sortCatalogThreadsScreen)
      },
      onToolbarOverflowMenuClicked = {
        navigationRouter.presentScreen(
          FloatingMenuScreen(
            floatingMenuKey = FloatingMenuScreen.CATALOG_OVERFLOW,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            menuItems = floatingMenuItems,
            onMenuItemClicked = { menuItem ->
              catalogScreenToolbarActionHandler.processClickedToolbarMenuItem(
                componentActivity = componentActivity,
                navigationRouter = navigationRouter,
                menuItem = menuItem
              )
            }
          )
        )
      }
    )
  }

  @Composable
  override fun Content() {
    RouterHost(
      screenKey = screenKey,
      navigationRouter = navigationRouter,
      defaultScreen = { CatalogPostListScreenContent() }
    )
  }

  @Composable
  private fun CatalogPostListScreenContent() {
    Box(modifier = Modifier.fillMaxSize()) {
      CatalogPostListScreen()
    }
  }

  @Composable
  private fun BoxScope.CatalogPostListScreen() {
    val configuration = LocalConfiguration.current
    val windowInsets = LocalWindowInsets.current
    val mainUiLayoutMode = LocalMainUiLayoutMode.current

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val fabSize = dimensionResource(id = R.dimen.fab_size)
    val fabVertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)

    val kurobaSnackbarState = rememberKurobaSnackbarState(snackbarManager = snackbarManager)
    val postCellCommentTextSizeSp by uiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by uiInfoManager.postCellSubjectTextSizeSp.collectAsState()

    val postListOptions by remember {
      derivedStateOf {
        PostListOptions(
          isCatalogMode = isCatalogScreen,
          isInPopup = false,
          pullToRefreshEnabled = true,
          contentPadding = PaddingValues(
            top = toolbarHeight + windowInsets.top,
            bottom = windowInsets.bottom + fabSize + fabVertOffset
          ),
          mainUiLayoutMode = uiInfoManager.mainUiLayoutMode(configuration),
          postCellCommentTextSizeSp = postCellCommentTextSizeSp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
          detectLinkableClicks = true
        )
      }
    }

    PostListContent(
      modifier = Modifier.fillMaxSize(),
      postListOptions = postListOptions,
      postsScreenViewModel = catalogScreenViewModel,
      onPostCellClicked = { postData ->
        // TODO(KurobaEx): come up with a better solution than doing it manually
        uiInfoManager.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)

        val threadDescriptor = ThreadDescriptor(
          catalogDescriptor = postData.postDescriptor.catalogDescriptor,
          threadNo = postData.postNo
        )

        threadScreenViewModel.loadThread(threadDescriptor)
      },
      onLinkableClicked = { postData, linkable ->
        // no-op (for now?)
      },
      onPostRepliesClicked = { postDescriptor ->
        showRepliesForPost(PopupRepliesScreen.ReplyViewMode.RepliesFrom(postDescriptor))
      },
      onPostImageClicked = { chanDescriptor, postImageData ->
        val catalogDescriptor = chanDescriptor as CatalogDescriptor

        val mediaViewerScreen = MediaViewerScreen(
          mediaViewerParams = MediaViewerParams.Catalog(
            catalogDescriptor = catalogDescriptor,
            initialImageUrl = postImageData.fullImageUrl.toHttpUrl()
          ),
          componentActivity = componentActivity,
          navigationRouter = navigationRouter
        )

        navigationRouter.presentScreen(mediaViewerScreen)
      },
      onPostListScrolled = { delta ->
        uiInfoManager.onChildContentScrolling(screenKey, delta)
      },
      onPostListTouchingTopOrBottomStateChanged = { touching ->
        uiInfoManager.onPostListTouchingTopOrBottomStateChanged(screenKey, touching)
      },
      onPostListDragStateChanged = { dragging ->
        uiInfoManager.onPostListDragStateChanged(screenKey, dragging)
      },
      onFastScrollerDragStateChanged = { dragging ->
        uiInfoManager.onFastScrollerDragStateChanged(screenKey, dragging)
      }
    )

    if (mainUiLayoutMode == MainUiLayoutMode.Split) {
      PostsScreenFloatingActionButton(
        screenKey = screenKey,
        screenContentLoadedFlow = screenContentLoadedFlow,
        mainUiLayoutMode = mainUiLayoutMode,
        homeScreenViewModel = homeScreenViewModel,
        uiInfoManager = uiInfoManager,
        snackbarManager = snackbarManager
      )
    }

    KurobaSnackbarContainer(
      modifier = Modifier.fillMaxSize(),
      screenKey = screenKey,
      uiInfoManager = uiInfoManager,
      snackbarManager = snackbarManager,
      kurobaSnackbarState = kurobaSnackbarState
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("CatalogScreen")
  }

}