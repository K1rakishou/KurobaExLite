package com.github.k1rakishou.kurobaexlite.features.posts.thread

import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.features.posts.search.image.RemoteImageSearchScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenLocalSearchToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.thread.toolbar.ThreadScreenDefaultToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.thread.toolbar.ThreadScreenReplyToolbar
import com.github.k1rakishou.kurobaexlite.features.reply.IReplyLayoutState
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutContainer
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutVisibility
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class ThreadScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen<KurobaChildToolbar>(screenArgs, componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val replyLayoutViewModel: ReplyLayoutViewModel by componentActivity.viewModel()
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)

  private val threadScreenToolbarActionHandler by lazy {
    ThreadScreenToolbarActionHandler()
  }

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val replyLayoutState: IReplyLayoutState
    get() = replyLayoutViewModel.getOrCreateReplyLayoutState(threadScreenViewModel.chanDescriptor)

  override val defaultToolbar: KurobaChildToolbar by lazy {
    ThreadScreenDefaultToolbar(
      bookmarksManager = bookmarksManager,
      threadScreenViewModel = threadScreenViewModel,
      parsedPostDataCache = parsedPostDataCache,
      globalUiInfoManager = globalUiInfoManager,
      onBackPressed = { globalUiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY, true) },
      toggleBookmarkState = { threadScreenViewModel.bookmarkOrUnbookmarkThread() },
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
                componentActivity = componentActivity,
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
      closeReplyLayout = { replyLayoutState.onBackPressed() },
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
      onToolbarCreated = { globalUiInfoManager.onChildScreenSearchStateChanged(screenKey, true) },
      onToolbarDisposed = { globalUiInfoManager.onChildScreenSearchStateChanged(screenKey, false) },
      onSearchQueryUpdated = { searchQuery -> threadScreenViewModel.updateSearchQuery(searchQuery) },
      onGlobalSearchIconClicked = { unreachable() },
      closeSearch = { toolbarKey -> kurobaToolbarContainerState.popToolbar(toolbarKey) }
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
            currentPage.screenKey == screenKey || currentPage.screenKey == CatalogScreen.SCREEN_KEY
          }
        }
      },
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    HandleBackPresses {
      for (composeScreen in navigationRouter.navigationScreensStackExcept(this).asReversed()) {
        if (composeScreen.onBackPressed()) {
          return@HandleBackPresses true
        }
      }

      if (replyLayoutState.onBackPressed()) {
        return@HandleBackPresses true
      }

      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
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

    Box(modifier = Modifier.fillMaxSize()) {
      ThreadPostListScreen()
    }
  }

  @Composable
  private fun BoxScope.ThreadPostListScreen() {
    val windowInsets = LocalWindowInsets.current
    val context = LocalContext.current

    val orientationMut by globalUiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val mainUiLayoutModeMut by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
    val mainUiLayoutMode = mainUiLayoutModeMut ?: return

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val fabVertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)
    var replyLayoutContainerHeight by remember { mutableStateOf(0.dp) }

    val kurobaSnackbarState = rememberKurobaSnackbarState()
    val postCellCommentTextSizeSp by globalUiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by globalUiInfoManager.postCellSubjectTextSizeSp.collectAsState()
    val replyLayoutVisibilityInfoStateForScreen by globalUiInfoManager.replyLayoutVisibilityInfoStateForScreen(screenKey)

    val postListOptions by remember(key1 = windowInsets, key2 = replyLayoutVisibilityInfoStateForScreen) {
      derivedStateOf {
        val bottomPadding = when (replyLayoutVisibilityInfoStateForScreen) {
          ReplyLayoutVisibility.Closed -> windowInsets.bottom
          ReplyLayoutVisibility.Opened,
          ReplyLayoutVisibility.Expanded -> windowInsets.bottom + replyLayoutContainerHeight
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

    PostListContent(
      modifier = Modifier.fillMaxSize(),
      postListOptions = postListOptions,
      postsScreenViewModel = threadScreenViewModel,
      onPostCellClicked = { postCellData ->
      },
      onPostCellLongClicked = { postCellData ->
        postLongtapContentMenu.showMenu(
          postListOptions = postListOptions,
          postCellData = postCellData,
          reparsePostsFunc = { postDescriptors ->
            val threadDescriptor = threadScreenViewModel.threadDescriptor
            if (threadDescriptor == null) {
              return@showMenu
            }

            threadScreenViewModel.reparsePostsByDescriptors(
              chanDescriptor = threadDescriptor,
              postDescriptors = postDescriptors
            )
          }
        )
      },
      onLinkableClicked = { postCellData, linkable ->
        linkableClickHelper.processClickedLinkable(
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
          showRepliesForPostFunc = { replyViewMode -> showRepliesForPost(replyViewMode) }
        )
      },
      onLinkableLongClicked = { postCellData, linkable ->
        linkableClickHelper.processLongClickedLinkable(
          sourceScreenKey = screenKey,
          postCellData = postCellData,
          linkable = linkable
        )
      },
      onPostRepliesClicked = { postDescriptor ->
        showRepliesForPost(PopupRepliesScreen.ReplyViewMode.RepliesFrom(postDescriptor))
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
      onPostImageClicked = { chanDescriptor, postImageDataResult, thumbnailBoundsInRoot ->
        val postImageData = if (postImageDataResult.isFailure) {
          snackbarManager.errorToast(
            message = postImageDataResult.exceptionOrThrow().errorMessageOrClassName(),
            screenKey = screenKey
          )

          return@PostListContent
        } else {
          postImageDataResult.getOrThrow()
        }

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

    val currentThreadDescriptor by threadScreenViewModel.currentlyOpenedThreadFlow.collectAsState()

    val replyLayoutVisibility by replyLayoutViewModel.getOrCreateReplyLayoutState(currentThreadDescriptor)
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
            if (!kurobaToolbarContainerState.contains(replyToolbar.toolbarKey)) {
              kurobaToolbarContainerState.setToolbar(replyToolbar)
            }
          }
        }
      }
    )

    if (mainUiLayoutMode == MainUiLayoutMode.Split) {
      val lastLoadError by threadScreenViewModel.postScreenState.lastLoadErrorState.collectAsState()
      val screenContentLoaded by screenContentLoadedFlow.collectAsState()
      val lastLoadedEndedWithError by remember { derivedStateOf { lastLoadError != null } }

      PostsScreenFloatingActionButton(
        screenKey = screenKey,
        screenContentLoaded = screenContentLoaded,
        lastLoadedEndedWithError = lastLoadedEndedWithError,
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

    if (!postListOptions.isInPopup) {
      PostListSearchButtons(
        postsScreenViewModel = threadScreenViewModel,
        searchToolbar = localSearchToolbar
      )
    }

    ReplyLayoutContainer(
      chanDescriptor = threadScreenViewModel.chanDescriptor,
      replyLayoutState = replyLayoutState,
      replyLayoutViewModel = replyLayoutViewModel,
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

  companion object {
    private const val TAG = "ThreadScreen"
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}