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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.reply.IReplyLayoutState
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutContainer
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutVisibility
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class ThreadScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val replyLayoutViewModel: ReplyLayoutViewModel by componentActivity.viewModel()
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val threadScreenToolbarActionHandler by lazy {
    ThreadScreenToolbarActionHandler()
  }

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter)
  }

  private val replyLayoutState: IReplyLayoutState
    get() = replyLayoutViewModel.getOrCreateReplyLayoutState(threadScreenViewModel.chanDescriptor)

  private val replyLayoutToolbarState = KurobaToolbarState(
    leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_close_24),
    middlePartInfo = MiddlePartInfo(centerContent = false),
    rightPartInfo = RightPartInfo(
      ToolbarIcon(ReplyToolbarIcons.PickLocalFile, R.drawable.ic_baseline_attach_file_24)
    )
  )

  private val threadToolbarState by lazy {
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
        ToolbarIcon(ThreadToolbarIcons.Search, R.drawable.ic_baseline_search_24),
        ToolbarIcon(ThreadToolbarIcons.Overflow, R.drawable.ic_baseline_more_vert_24),
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

    val threadDescriptor by threadScreenViewModel.currentlyOpenedThreadFlow.collectAsState()
    val replyLayoutVisibility by replyLayoutStateByDescriptor(threadDescriptor).replyLayoutVisibilityState

    if (replyLayoutVisibility != ReplyLayoutVisibility.Closed) {
      ReplyLayoutToolbar(mainUiLayoutMode)
    } else {
      ThreadScreenToolbar(mainUiLayoutMode)
    }
  }

  @Composable
  private fun ReplyLayoutToolbar(mainUiLayoutMode: MainUiLayoutMode) {
    LaunchedEffect(
      key1 = Unit,
      // TODO(KurobaEx): strings
      block = { replyLayoutToolbarState.toolbarTitleState.value = "Reply" }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        replyLayoutToolbarState.toolbarIconClickEventFlow.collect { key ->
          when (key as ReplyToolbarIcons) {
            ReplyToolbarIcons.PickLocalFile -> {
              val threadDescriptor = threadScreenViewModel.threadDescriptor
                ?: return@collect

              replyLayoutViewModel.onPickFileRequested(threadDescriptor)
            }
          }
        }
      }
    )

    KurobaToolbar(
      screenKey = screenKey,
      kurobaToolbarState = replyLayoutToolbarState,
      canProcessBackEvent = { canProcessBackEvent(mainUiLayoutMode, uiInfoManager.currentPage()) },
      onLeftIconClicked = { replyLayoutState.onBackPressed() },
      onMiddleMenuClicked = null,
      onSearchQueryUpdated = null
    )
  }

  @Composable
  private fun ThreadScreenToolbar(
    mainUiLayoutMode: MainUiLayoutMode
  ) {
    UpdateToolbarTitle(
      parsedPostDataCache = parsedPostDataCache,
      postScreenState = threadScreenViewModel.postScreenState,
      kurobaToolbarState = threadToolbarState
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        threadToolbarState.toolbarIconClickEventFlow.collect { key ->
          when (key as ThreadToolbarIcons) {
            ThreadToolbarIcons.Search -> threadToolbarState.openSearch()
            ThreadToolbarIcons.Overflow -> {
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
      kurobaToolbarState = threadToolbarState,
      canProcessBackEvent = {
        canProcessBackEvent(
          mainUiLayoutMode,
          uiInfoManager.currentPage()
        )
      },
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
      if (replyLayoutState.onBackPressed()) {
        return@HandleBackPresses true
      }

      if (threadToolbarState.onBackPressed()) {
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

    ProcessCaptchaRequestEvents(
      homeScreenViewModel = homeScreenViewModel,
      currentChanDescriptor = { threadScreenViewModel.threadDescriptor }
    )

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
    val replyLayoutContainerOpenedHeight = dimensionResource(id = R.dimen.reply_layout_container_opened_height)

    val kurobaSnackbarState = rememberKurobaSnackbarState()
    val postCellCommentTextSizeSp by uiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by uiInfoManager.postCellSubjectTextSizeSp.collectAsState()
    val replyLayoutVisibilityInfoStateForScreen by uiInfoManager.replyLayoutVisibilityInfoStateForScreen(screenKey)

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
          ownerScreenKey = screenKey,
          pullToRefreshEnabled = true,
          contentPadding = PaddingValues(
            top = toolbarHeight + windowInsets.top,
            bottom = bottomPadding + fabVertOffset
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
      postsScreenViewModel = threadScreenViewModel,
      onPostCellClicked = { postCellData ->
      },
      onPostCellLongClicked = { postCellData ->
        coroutineScope.launch {
          postLongtapContentMenu.showMenu(
            coroutineScope = coroutineScope,
            postListOptions = postListOptions,
            postCellData = postCellData,
            reparsePostsFunc = { postDescriptors ->
              threadScreenViewModel.reparsePostsByDescriptors(postDescriptors)
            }
          )
        }
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
      onQuotePostClicked = { postCellData ->
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@PostListContent

        replyLayoutViewModel.quotePost(threadDescriptor, postCellData)
      },
      onQuotePostWithCommentClicked = { postCellData ->
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@PostListContent

        replyLayoutViewModel.quotePostWithComment(threadDescriptor, postCellData)
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
        uiInfoManager = uiInfoManager,
        snackbarManager = snackbarManager,
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
      chanDescriptor = threadScreenViewModel.chanDescriptor,
      replyLayoutState = replyLayoutState,
      replyLayoutViewModel = replyLayoutViewModel,
      onAttachedMediaClicked = { attachedMedia ->
        // TODO(KurobaEx): show options
      }
    )

    KurobaSnackbarContainer(
      modifier = Modifier.fillMaxSize(),
      screenKey = screenKey,
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

  private enum class ThreadToolbarIcons {
    Search,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}