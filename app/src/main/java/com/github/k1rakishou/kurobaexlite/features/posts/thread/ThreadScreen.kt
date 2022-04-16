package com.github.k1rakishou.kurobaexlite.features.posts.thread

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
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
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.RightPartInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class ThreadScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val threadScreenToolbarActionHandler by lazy {
    ThreadScreenToolbarActionHandler()
  }

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter)
  }

  private val kurobaToolbarState by lazy {
    val mainUiLayoutMode = requireNotNull(uiInfoManager.currentUiLayoutModeState.value) {
      "currentUiLayoutModeState is not initialized yet!"
    }

    val leftIconInfo = when (mainUiLayoutMode) {
      MainUiLayoutMode.Portrait -> LeftIconInfo(R.drawable.ic_baseline_arrow_back_24)
      MainUiLayoutMode.Split -> null
    }

    return@lazy KurobaToolbarState(
      leftIconInfo = leftIconInfo,
      middlePartInfo = MiddlePartInfo(centerContent = false),
      rightPartInfo = RightPartInfo(
        ToolbarIcon(ToolbarIcons.Search, R.drawable.ic_baseline_search_24),
        ToolbarIcon(ToolbarIcons.Overflow, R.drawable.ic_baseline_more_vert_24),
      ),
      postScreenToolbarInfo = PostScreenToolbarInfo(isCatalogScreen = false)
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = false
  override val screenContentLoadedFlow: StateFlow<Boolean> = threadScreenViewModel.postScreenState.contentLoaded

  private val floatingMenuItems: List<FloatingMenuItem> by lazy {
    listOf(
      FloatingMenuItem.Text(
        menuItemKey = ThreadScreenToolbarActionHandler.ACTION_RELOAD,
        text = FloatingMenuItem.MenuItemText.Id(R.string.reload)
      ),
      FloatingMenuItem.Text(
        menuItemKey = ThreadScreenToolbarActionHandler.ACTION_COPY_THREAD_URL,
        text = FloatingMenuItem.MenuItemText.Id(R.string.thread_toolbar_action_copy_thread_url)
      ),
      FloatingMenuItem.Text(
        menuItemKey = ThreadScreenToolbarActionHandler.ACTION_THREAD_ALBUM,
        text = FloatingMenuItem.MenuItemText.Id(R.string.thread_toolbar_album)
      ),
      FloatingMenuItem.Footer(
        items = listOf(
          FloatingMenuItem.Icon(
            menuItemKey = ThreadScreenToolbarActionHandler.ACTION_SCROLL_TOP,
            iconId = R.drawable.ic_baseline_arrow_upward_24
          ),
          FloatingMenuItem.Icon(
            menuItemKey = ThreadScreenToolbarActionHandler.ACTION_SCROLL_BOTTOM,
            iconId = R.drawable.ic_baseline_arrow_downward_24
          )
        )
      )
    )
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val mainUiLayoutModeMut by uiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut ?: return

    UpdateToolbarTitle(
      parsedPostDataCache = parsedPostDataCache,
      postScreenState = threadScreenViewModel.postScreenState,
      kurobaToolbarState = kurobaToolbarState
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        kurobaToolbarState.toolbarIconClickEventFlow.collect { key ->
          when (key as ToolbarIcons) {
            ToolbarIcons.Search -> kurobaToolbarState.openSearch()
            ToolbarIcons.Overflow -> {
              navigationRouter.presentScreen(
                FloatingMenuScreen(
                  floatingMenuKey = FloatingMenuScreen.THREAD_OVERFLOW,
                  componentActivity = componentActivity,
                  navigationRouter = navigationRouter,
                  menuItems = floatingMenuItems,
                  onMenuItemClicked = { menuItem ->
                    threadScreenToolbarActionHandler.processClickedToolbarMenuItem(
                      componentActivity = componentActivity,
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
      kurobaToolbarState = kurobaToolbarState,
      canProcessBackEvent = { canProcessBackEvent(mainUiLayoutMode, uiInfoManager.currentPage()) },
      onLeftIconClicked = { uiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY) },
      onMiddleMenuClicked = null,
      onSearchQueryUpdated = { searchQuery ->
        uiInfoManager.onChildScreenSearchStateChanged(screenKey, searchQuery)
        threadScreenViewModel.updateSearchQuery(searchQuery)
      }
    )
  }

  @Composable
  override fun Content() {
    HandleBackPresses {
      if (kurobaToolbarState.onBackPressed()) {
        return@HandleBackPresses true
      }

      for (composeScreen in navigationRouter.navigationScreensStack.asReversed()) {
        if (composeScreen.onBackPressed()) {
          return@HandleBackPresses true
        }
      }

      if (threadScreenViewModel.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses false
    }

    RouterHost(
      navigationRouter = navigationRouter,
      defaultScreen = { ThreadPostListScreenContent() }
    )
  }

  @Composable
  private fun ThreadPostListScreenContent() {
    Box(modifier = Modifier.fillMaxSize()) {
      ThreadPostListScreen()
    }
  }

  @Composable
  private fun BoxScope.ThreadPostListScreen() {
    val windowInsets = LocalWindowInsets.current
    val context = LocalContext.current

    val orientationMut by uiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val mainUiLayoutModeMut by uiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut ?: return

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val fabVertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)

    val kurobaSnackbarState = rememberKurobaSnackbarState()
    val postCellCommentTextSizeSp by uiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by uiInfoManager.postCellSubjectTextSizeSp.collectAsState()

    val postListOptions by remember {
      derivedStateOf {
        PostListOptions(
          isCatalogMode = isCatalogScreen,
          isInPopup = false,
          ownerScreenKey = screenKey,
          pullToRefreshEnabled = true,
          contentPadding = PaddingValues(
            top = toolbarHeight + windowInsets.top,
            bottom = windowInsets.bottom + fabVertOffset
          ),
          mainUiLayoutMode = mainUiLayoutMode,
          postCellCommentTextSizeSp = postCellCommentTextSizeSp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
          detectLinkableClicks = true,
          orientation = orientation
        )
      }
    }

    PostListContent(
      modifier = Modifier.fillMaxSize(),
      postListOptions = postListOptions,
      postsScreenViewModel = threadScreenViewModel,
      onPostCellClicked = { postCellData ->
        // no-op
      },
      onLinkableClicked = { postCellData, linkable ->
        linkableClickHelper.processClickedLinkable(
          context = context,
          postCellData = postCellData,
          linkable = linkable,
          loadThreadFunc = { threadDescriptor ->
            threadScreenViewModel.loadThread(threadDescriptor)
          },
          loadCatalogFunc = { catalogDescriptor ->
            catalogScreenViewModel.loadCatalog(catalogDescriptor)
          },
          showRepliesForPostFunc = { replyViewMode -> showRepliesForPost(replyViewMode) }
        )
      },
      onPostRepliesClicked = { postDescriptor ->
        showRepliesForPost(PopupRepliesScreen.ReplyViewMode.RepliesFrom(postDescriptor))
      },
      onPostImageClicked = { chanDescriptor, postImageData, thumbnailBoundsInRoot ->
        val threadDescriptor = chanDescriptor as ThreadDescriptor
        clickedThumbnailBoundsStorage.storeBounds(postImageData, thumbnailBoundsInRoot)

        val mediaViewerScreen = MediaViewerScreen(
          mediaViewerParams = MediaViewerParams.Thread(
            threadDescriptor = threadDescriptor,
            initialImageUrl = postImageData.fullImageAsUrl
          ),
          openedFromScreen = screenKey,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter
        )

        navigationRouter.presentScreen(mediaViewerScreen)
      },
      onPostListScrolled = { delta ->
        uiInfoManager.onChildContentScrolling(screenKey, delta)
      },
      onPostListTouchingTopOrBottomStateChanged = { touchingBottom ->
        uiInfoManager.onPostListTouchingTopOrBottomStateChanged(screenKey, touchingBottom)
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
      kurobaSnackbarState = kurobaSnackbarState
    )
  }

  private enum class ToolbarIcons {
    Search,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}