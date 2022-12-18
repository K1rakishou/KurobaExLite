package com.github.k1rakishou.kurobaexlite.features.posts.thread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.main.LocalMainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.posts.bookmark.NewBookmarkOptionsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupPostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.search.image.RemoteImageSearchScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostImageLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostListSearchButtons
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreenFabContainer
import com.github.k1rakishou.kurobaexlite.features.posts.shared.ProcessCaptchaRequestEvents
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListSelectionState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.rememberPostListSelectionState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenLocalSearchToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenSelectionToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.thread.toolbar.ThreadScreenDefaultToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.thread.toolbar.ThreadScreenReplyToolbar
import com.github.k1rakishou.kurobaexlite.features.reply.IReplyLayoutState
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutContainer
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutVisibility
import com.github.k1rakishou.kurobaexlite.features.screenshot.PostScreenshotScreen
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.DisposableElement
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.GenericLazyStateWrapper
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.LazyListStateWrapper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class ThreadScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen<KurobaChildToolbar>(screenArgs, componentActivity, navigationRouter) {
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val replyLayoutViewModel: ReplyLayoutViewModel by componentActivity.viewModel()
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)

  private val threadScreenToolbarActionHandler by lazy {
    ThreadScreenToolbarActionHandler(componentActivity)
  }

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val postImageLongtapContentMenu by lazy {
    PostImageLongtapContentMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val replyLayoutState: IReplyLayoutState
    get() = replyLayoutViewModel.getOrCreateReplyLayoutState(threadScreenViewModel.chanDescriptor)

  override val defaultToolbar: KurobaChildToolbar by lazy {
    ThreadScreenDefaultToolbar(
      threadScreenViewModel = threadScreenViewModel,
      onBackPressed = {
        if (processBackPressEvent()) {
          return@ThreadScreenDefaultToolbar
        }

        globalUiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY, true)
      },
      toggleBookmarkState = { longClicked ->
        val bookmarkDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@ThreadScreenDefaultToolbar

        if (bookmarksManager.contains(bookmarkDescriptor)) {
          threadScreenViewModel.bookmarkOrUnbookmarkThread()
          return@ThreadScreenDefaultToolbar
        }

        if (longClicked) {
          dialogSettings.doNotShowNewBookmarkDialogOptions.write(false)
        }

        if (dialogSettings.doNotShowNewBookmarkDialogOptions.read()) {
          threadScreenViewModel.bookmarkOrUnbookmarkThread()
          return@ThreadScreenDefaultToolbar
        }

        val newBookmarkOptionsScreen = ComposeScreen.createScreen<NewBookmarkOptionsScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          callbacks = {
            callback(
              callbackKey = NewBookmarkOptionsScreen.ON_FINISHED,
              func = { threadScreenViewModel.bookmarkOrUnbookmarkThread()  }
            )
          }
        )

        navigationRouter.presentScreen(newBookmarkOptionsScreen)
      },
      openThreadAlbum = {
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@ThreadScreenDefaultToolbar

        val albumScreen = ComposeScreen.createScreen<AlbumScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          args = { putParcelable(AlbumScreen.CHAN_DESCRIPTOR_ARG, threadDescriptor) }
        )

        navigationRouter.pushScreen(albumScreen)
      },
      showLocalSearchToolbar = { kurobaToolbarContainerState.setToolbar(localSearchToolbar) },
      showOverflowMenu = {
        navigationRouter.presentScreen(
          FloatingMenuScreen(
            floatingMenuKey = FloatingMenuScreen.THREAD_OVERFLOW,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            menuItems = floatingMenuItems,
            onMenuItemClicked = { menuItem ->
              threadScreenToolbarActionHandler.processClickedToolbarMenuItem(
                menuItem = menuItem
              )
            }
          )
        )
      }
    )
  }

  private val replyToolbar: KurobaChildToolbar by lazy {
    ThreadScreenReplyToolbar(
      threadScreenViewModel = threadScreenViewModel,
      closeReplyLayout = { replyLayoutState.collapseReplyLayout() },
      pickLocalFile = {
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@ThreadScreenReplyToolbar

        replyLayoutViewModel.pickLocalFile(threadDescriptor)
      },
      imageRemoteSearch = {
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@ThreadScreenReplyToolbar

        val remoteImageSearchScreen = ComposeScreen.createScreen<RemoteImageSearchScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          callbacks = {
            callback<String>(
              callbackKey = RemoteImageSearchScreen.ON_IMAGE_SELECTED,
              func = { selectedImageUrl ->
                replyLayoutViewModel.pickRemoteFile(
                  componentActivityInput = componentActivity,
                  navigationRouterInput = navigationRouter,
                  chanDescriptor = threadDescriptor,
                  fileUrl = selectedImageUrl
                )
              }
            )
          }
        )

        navigationRouter.pushScreen(remoteImageSearchScreen)
      }
    )
  }

  private val localSearchToolbar: PostsScreenLocalSearchToolbar by lazy {
    PostsScreenLocalSearchToolbar(
      screenKey = screenKey,
      currentSiteKey = { threadScreenViewModel.threadDescriptor?.siteKey },
      onToolbarCreated = { globalUiInfoManager.onChildScreenSearchStateChanged(screenKey, true) },
      onToolbarDisposed = { globalUiInfoManager.onChildScreenSearchStateChanged(screenKey, false) },
      onSearchQueryUpdated = { searchQuery -> threadScreenViewModel.updateSearchQuery(searchQuery) },
      onGlobalSearchIconClicked = { unreachable() },
      showFoundPostsInPopup = { foundPostDescriptors ->
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@PostsScreenLocalSearchToolbar

        showFoundPostsInPopup(threadDescriptor, foundPostDescriptors)
      },
      closeSearch = { toolbarKey -> kurobaToolbarContainerState.popToolbar(toolbarKey) }
    )
  }

  private val selectionToolbar: PostsScreenSelectionToolbar<PostsScreenSelectionToolbar.State.SelectablePost> by lazy {
    PostsScreenSelectionToolbar<PostsScreenSelectionToolbar.State.SelectablePost>(
      screenKey = screenKey,
      onCancelSelection = {
        kurobaToolbarContainerState.popToolbar(
          expectedKey = selectionToolbar.toolbarKey,
          withAnimation = false
        )
      },
      onScreenshotPosts = { selectedPosts ->
        val threadDescriptor = threadScreenViewModel.threadDescriptor
          ?: return@PostsScreenSelectionToolbar

        val postScreenshotScreen = ComposeScreen.createScreen<PostScreenshotScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          args = {
            putParcelable(
              PostScreenshotScreen.CHAN_DESCRIPTOR,
              threadDescriptor
            )

            putParcelableArray(
              PostScreenshotScreen.POST_DESCRIPTORS,
              selectedPosts.map { it.postDescriptor }.toTypedArray()
            )
          }
        )

        navigationRouter.presentScreen(postScreenshotScreen)

        kurobaToolbarContainerState.popToolbar(
          expectedKey = selectionToolbar.toolbarKey,
          withAnimation = false
        )
      }
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<KurobaChildToolbar>(screenKey)
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
    val currentUiLayoutMode = LocalMainUiLayoutMode.current
    val currentUiLayoutModeUpdated by rememberUpdatedState(newValue = currentUiLayoutMode)

    KurobaToolbarContainer(
      toolbarContainerKey = screenKey.key,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = {
        val currentPage = globalUiInfoManager.currentPage()
          ?: return@KurobaToolbarContainer false

        return@KurobaToolbarContainer when (currentUiLayoutModeUpdated) {
          MainUiLayoutMode.Phone -> {
            currentPage.screenKey == screenKey
          }
          MainUiLayoutMode.Split -> {
            currentPage.screenKey == screenKey || currentPage.screenKey == CatalogScreen.SCREEN_KEY
          }
        }
      },
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    val view = LocalView.current

    LaunchedEffect(
      key1 = Unit,
      block = {
        threadScreenViewModel.displayPostsPopupScreenFlow.collectLatest { popupPostViewMode ->
          val popupPostsScreen = ComposeScreen.createScreen<PopupPostsScreen>(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            args = { putParcelable(PopupPostsScreen.REPLY_VIEW_MODE, popupPostViewMode) }
          )

          navigationRouter.presentScreen(popupPostsScreen)
        }
      }
    )

    HandleBackPresses { processBackPressEvent() }

    ProcessCaptchaRequestEvents(
      currentChanDescriptorProvider = { threadScreenViewModel.threadDescriptor },
      componentActivityProvider = { componentActivity },
      navigationRouterProvider = { navigationRouter }
    )

    val postListSelectionState = rememberPostListSelectionState()
    val screenContentLoaded by screenContentLoadedFlow.collectAsState()
    val currentThreadDescriptor by threadScreenViewModel.currentlyOpenedThreadFlow.collectAsState()

    LaunchedEffect(
      key1 = currentThreadDescriptor,
      block = {
        val replyLayoutState = replyLayoutViewModel.getOrCreateReplyLayoutState(currentThreadDescriptor)

        snapshotFlow { replyLayoutState.replyLayoutVisibilityState.value }
          .collect { replyLayoutVisibility ->
            when (replyLayoutVisibility) {
              ReplyLayoutVisibility.Collapsed -> {
                kurobaToolbarContainerState.popToolbar(replyToolbar.toolbarKey)
              }
              ReplyLayoutVisibility.Opened,
              ReplyLayoutVisibility.Expanded -> {
                if (!kurobaToolbarContainerState.contains(replyToolbar.toolbarKey)) {
                  kurobaToolbarContainerState.setToolbar(replyToolbar)
                }
              }
            }
          }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        postListSelectionState.selectedItemsUpdateFlow.collectLatest { selectedPostDescriptors ->
          selectionToolbar.onSelectedPostsUpdated(selectedPostDescriptors)

          globalUiInfoManager.screenIsInPostSelectionModeStateChanged(
            screenKey = screenKey,
            isInPostSelectionMode = selectedPostDescriptors.isNotEmpty()
          )

          if (selectedPostDescriptors.isEmpty()) {
            kurobaToolbarContainerState.popToolbar(selectionToolbar.toolbarKey)
          }
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        selectionToolbar.lifecycleEvents.collectLatest { lifecycle ->
          if (lifecycle != DisposableElement.Lifecycle.Disposed) {
            return@collectLatest
          }

          postListSelectionState.clearSelection()
        }
      }
    )

    Box(modifier = Modifier.fillMaxSize()) {
      ThreadPostListScreen(
        screenContentLoaded = screenContentLoaded,
        screenKey = screenKey,
        isCatalogScreen = isCatalogScreen,
        postListSelectionState = postListSelectionState,
        replyLayoutStateProvider = { replyLayoutState },
        postLongtapContentMenuProvider = { postLongtapContentMenu },
        linkableClickHelperProvider = { linkableClickHelper },
        navigationRouterProvider = { navigationRouter },
        showRepliesForPost = { postViewMode -> showRepliesForPost(postViewMode) },
        onPostImageClicked = { chanDescriptor, postImageData, thumbnailBoundsInRoot ->
          val threadDescriptor = chanDescriptor as ThreadDescriptor
          clickedThumbnailBoundsStorage.storeBounds(postImageData, thumbnailBoundsInRoot)

          val mediaViewerScreen = ComposeScreen.createScreen<MediaViewerScreen>(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            args = {
              val mediaViewerParams = MediaViewerParams.Thread(
                chanDescriptor = threadDescriptor,
                initialImageUrlString = postImageData.fullImageAsString
              )

              putParcelable(MediaViewerScreen.mediaViewerParamsKey, mediaViewerParams)
              putParcelable(MediaViewerScreen.openedFromScreenKey, screenKey)
            }
          )

          navigationRouter.presentScreen(mediaViewerScreen)
        },
        onPostImageLongClicked = { chanDescriptor, longClickedImage ->
          postImageLongtapContentMenu.showMenu(
            postImage = longClickedImage,
            viewProvider = { view }
          )
        },
        postListSearchButtons = {
          PostListSearchButtons(
            postsScreenViewModelProvider = { threadScreenViewModel },
            searchToolbarProvider = { localSearchToolbar },
            kurobaToolbarContainerStateProvider = { kurobaToolbarContainerState }
          )
        },
        startPostSelection = { postDescriptor ->
          kurobaToolbarContainerState.setToolbar(selectionToolbar)
          postListSelectionState.toggleSelection(postDescriptor)
        },
      )
    }
  }

  private suspend fun processBackPressEvent(): Boolean {
    for (composeScreen in navigationRouter.navigationScreensStackExcept(this).asReversed()) {
      if (composeScreen.onBackPressed()) {
        return true
      }
    }

    if (replyLayoutState.onBackPressed()) {
      return true
    }

    if (kurobaToolbarContainerState.onBackPressed()) {
      return true
    }

    if (threadScreenViewModel.onBackPressed()) {
      return true
    }

    return false
  }

  companion object {
    private const val TAG = "ThreadScreen"
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}


@Composable
private fun BoxScope.ThreadPostListScreen(
  screenContentLoaded: Boolean,
  screenKey: ScreenKey,
  isCatalogScreen: Boolean,
  postListSelectionState: PostListSelectionState,
  replyLayoutStateProvider: () -> IReplyLayoutState,
  postLongtapContentMenuProvider: () -> PostLongtapContentMenu,
  linkableClickHelperProvider: () -> LinkableClickHelper,
  navigationRouterProvider: () -> NavigationRouter,
  showRepliesForPost: (PopupPostsScreen.PopupPostViewMode) -> Unit,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  postListSearchButtons: @Composable () -> Unit,
  startPostSelection: (PostDescriptor) -> Unit
) {
  val catalogScreenViewModel = koinRememberViewModel<CatalogScreenViewModel>()
  val threadScreenViewModel = koinRememberViewModel<ThreadScreenViewModel>()
  val homeScreenViewModel = koinRememberViewModel<HomeScreenViewModel>()
  val replyLayoutViewModel = koinRememberViewModel<ReplyLayoutViewModel>()
  val snackbarManager = koinRemember<SnackbarManager>()
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val androidHelpers = koinRemember<AndroidHelpers>()

  val windowInsets = LocalWindowInsets.current
  val context = LocalContext.current
  val orientation = LocalConfiguration.current.orientation
  val mainUiLayoutMode = LocalMainUiLayoutMode.current
  val view = LocalView.current

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  var replyLayoutContainerHeight by remember { mutableStateOf(0.dp) }
  val viewProvider by rememberUpdatedState(newValue = { view })

  val kurobaSnackbarState = rememberKurobaSnackbarState()

  val postCellCommentTextSizeSp by globalUiInfoManager.postCellCommentTextSizeSp.collectAsState()
  val postCellSubjectTextSizeSp by globalUiInfoManager.postCellSubjectTextSizeSp.collectAsState()
  val replyLayoutVisibilityInfoStateForScreen by globalUiInfoManager.replyLayoutVisibilityInfoStateForScreen(screenKey)

  val postListOptions by remember(key1 = windowInsets, key2 = replyLayoutVisibilityInfoStateForScreen) {
    derivedStateOf {
      val bottomPadding = when (replyLayoutVisibilityInfoStateForScreen) {
        ReplyLayoutVisibility.Collapsed -> windowInsets.bottom
        ReplyLayoutVisibility.Opened,
        ReplyLayoutVisibility.Expanded -> windowInsets.bottom + replyLayoutContainerHeight
      }

      return@derivedStateOf PostListOptions(
        isCatalogMode = isCatalogScreen,
        showThreadStatusCell = true,
        textSelectionEnabled = true,
        isInPopup = false,
        openedFromScreenKey = screenKey,
        pullToRefreshEnabled = true,
        contentPadding = PaddingValues(
          top = toolbarHeight + windowInsets.top,
          bottom = bottomPadding
        ),
        mainUiLayoutMode = mainUiLayoutMode,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        detectLinkableClicks = true,
        orientation = orientation,
        postViewMode = PostViewMode.List
      )
    }
  }

  val _lazyListState = rememberLazyListState()
  val lazyListStateWrapper = remember(key1 = _lazyListState) { LazyListStateWrapper(_lazyListState) }

  PostListContent(
    modifier = Modifier.fillMaxSize(),
    lazyStateWrapper = lazyListStateWrapper as GenericLazyStateWrapper,
    postListOptions = postListOptions,
    postListSelectionState = postListSelectionState,
    postsScreenViewModelProvider = { threadScreenViewModel },
    onPostCellClicked = { postCellData ->
      // no-op
    },
    onPostCellLongClicked = { postCellData ->
      postLongtapContentMenuProvider().showMenu(
        postListOptions = postListOptions,
        postCellData = postCellData,
        viewProvider = viewProvider,
        reparsePostsFunc = { postDescriptors ->
          val threadDescriptor = threadScreenViewModel.threadDescriptor
          if (threadDescriptor == null) {
            return@showMenu
          }

          threadScreenViewModel.reparsePostsByDescriptors(
            chanDescriptor = threadDescriptor,
            postDescriptors = postDescriptors
          )
        },
        startPostSelection = startPostSelection
      )
    },
    onLinkableClicked = { postCellData, linkable ->
      linkableClickHelperProvider().processClickedLinkable(
        context = context,
        sourceScreenKey = screenKey,
        postCellData = postCellData,
        linkable = linkable,
        loadThreadFunc = { threadDescriptor ->
          threadScreenViewModel.loadThread(threadDescriptor)
        },
        loadCatalogFunc = { catalogDescriptor ->
          catalogScreenViewModel.loadCatalog(catalogDescriptor)
        },
        showRepliesForPostFunc = { postViewMode -> showRepliesForPost(postViewMode) }
      )
    },
    onLinkableLongClicked = { postCellData, linkable ->
      linkableClickHelperProvider().processLongClickedLinkable(
        sourceScreenKey = screenKey,
        postCellData = postCellData,
        linkable = linkable
      )
    },
    onPostRepliesClicked = { chanDescriptor, postDescriptor ->
      val popupPostViewMode = PopupPostsScreen.PopupPostViewMode.RepliesFrom(
        chanDescriptor = chanDescriptor,
        postDescriptor = postDescriptor
      )

      showRepliesForPost(popupPostViewMode)
    },
    onCopySelectedText = { selectedText ->
      androidHelpers.copyToClipboard("Selected text", selectedText)
    },
    onQuoteSelectedText = { withText, selectedText, postCellData ->
      val threadDescriptor = threadScreenViewModel.threadDescriptor
        ?: return@PostListContent

      if (withText) {
        replyLayoutViewModel.quotePostWithText(threadDescriptor, postCellData, selectedText)
      } else {
        replyLayoutViewModel.quotePost(threadDescriptor, postCellData)
      }
    },
    onPostImageClicked = onPostImageClicked,
    onPostImageLongClicked = onPostImageLongClicked,
    onGoToPostClicked = null,
    onPostListScrolled = { delta ->
      globalUiInfoManager.onContentListScrolling(screenKey, delta)
    },
    onPostListTouchingTopOrBottomStateChanged = { touchingBottom ->
      globalUiInfoManager.onContentListTouchingTopOrBottomStateChanged(screenKey, touchingBottom)
    },
    onCurrentlyTouchingPostList = { touching ->
      globalUiInfoManager.onCurrentlyTouchingContentList(screenKey, touching)
    },
    onFastScrollerDragStateChanged = { dragging ->
      globalUiInfoManager.onFastScrollerDragStateChanged(screenKey, dragging)
    }
  )

  if (mainUiLayoutMode == MainUiLayoutMode.Split) {
    val lastLoadError by threadScreenViewModel.postScreenState.lastLoadErrorState.collectAsState()
    val lastLoadedEndedWithError by remember { derivedStateOf { lastLoadError != null } }

    PostsScreenFabContainer(
      screenKey = screenKey,
      screenContentLoaded = screenContentLoaded,
      lastLoadedEndedWithError = lastLoadedEndedWithError,
      mainUiLayoutMode = mainUiLayoutMode,
      onReplyFabClicked = { clickedFabScreenKey ->
        if (screenKey != clickedFabScreenKey) {
          return@PostsScreenFabContainer
        }

        replyLayoutStateProvider().openReplyLayout()
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

          replyLayoutStateProvider().openReplyLayout()
        }
      }
    )
  }

  if (!postListOptions.isInPopup) {
    postListSearchButtons()
  }

  ReplyLayoutContainer(
    chanDescriptor = threadScreenViewModel.chanDescriptor,
    replyLayoutState = replyLayoutStateProvider(),
    navigationRouterProvider = navigationRouterProvider,
    onReplayLayoutHeightChanged = { newHeightDp -> replyLayoutContainerHeight = newHeightDp },
    onAttachedMediaClicked = { attachedMedia ->
      // TODO(KurobaEx): show options
      snackbarManager.toast(
        message = "Media editor is not implemented yet",
        screenKey = ThreadScreen.SCREEN_KEY
      )
    },
    onPostedSuccessfully = { postDescriptor ->
      // no-op
    }
  )

  KurobaSnackbarContainer(
    modifier = Modifier.fillMaxSize(),
    screenKey = screenKey,
    isTablet = globalUiInfoManager.isTablet,
    kurobaSnackbarState = kurobaSnackbarState
  )
}