package com.github.k1rakishou.kurobaexlite.features.posts.catalog

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.boards.CatalogSelectionScreen
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.history.HistoryScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.main.LocalMainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.toolbar.CatalogScreenDefaultToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.toolbar.CatalogScreenReplyToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.search.global.GlobalSearchScreen
import com.github.k1rakishou.kurobaexlite.features.posts.search.image.RemoteImageSearchScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostListSearchButtons
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.features.posts.shared.ProcessCaptchaRequestEvents
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
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class CatalogScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen<KurobaChildToolbar>(screenArgs, componentActivity, navigationRouter) {
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val replyLayoutViewModel: ReplyLayoutViewModel by componentActivity.viewModel()
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val catalogScreenToolbarActionHandler by lazy {
    CatalogScreenToolbarActionHandler(componentActivity, screenCoroutineScope)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter, screenCoroutineScope)
  }

  private val replyLayoutState: IReplyLayoutState
    get() = replyLayoutViewModel.getOrCreateReplyLayoutState(catalogScreenViewModel.chanDescriptor)

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<KurobaChildToolbar>(screenKey)
  }

  override val defaultToolbar: KurobaChildToolbar by lazy {
    CatalogScreenDefaultToolbar(
      catalogScreenViewModel = catalogScreenViewModel,
      onBackPressed = { globalUiInfoManager.openDrawer() },
      showCatalogSelectionScreen = {
        val catalogSelectionScreen = ComposeScreen.createScreen<CatalogSelectionScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          args = {
            putParcelable(
              CatalogSelectionScreen.CATALOG_DESCRIPTOR_ARG,
              catalogScreenViewModel.chanDescriptor as? CatalogDescriptor
            )
          }
        )

        navigationRouter.pushScreen(catalogSelectionScreen)
      },
      showSortCatalogThreadsScreen = {
        val sortCatalogThreadsScreen = ComposeScreen.createScreen<SortCatalogThreadsScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          callbacks = {
            callback(
              callbackKey = SortCatalogThreadsScreen.ON_APPLIED,
              func = { catalogScreenViewModel.onCatalogSortChanged() }
            )
          }
        )

        navigationRouter.presentScreen(sortCatalogThreadsScreen)
      },
      showOverflowMenu = {
        screenCoroutineScope.launch {
          navigationRouter.presentScreen(
            FloatingMenuScreen(
              floatingMenuKey = FloatingMenuScreen.CATALOG_OVERFLOW,
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              menuItems = floatingMenuItems(),
              onMenuItemClicked = { menuItem ->
                catalogScreenToolbarActionHandler.processClickedToolbarMenuItem(
                  navigationRouter = navigationRouter,
                  menuItem = menuItem
                )
              }
            )
          )
        }
      },
      showLocalSearchToolbar = {
        kurobaToolbarContainerState.setToolbar(localSearchToolbar)
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

        replyLayoutViewModel.pickLocalFile(catalogDescriptor)
      },
      imageRemoteSearch = {
        val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
          ?: return@CatalogScreenReplyToolbar

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
                  chanDescriptor = catalogDescriptor,
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
      onSearchQueryUpdated = { searchQuery -> catalogScreenViewModel.updateSearchQuery(searchQuery) },
      onGlobalSearchIconClicked = { currentQuery ->
        val descriptor = catalogScreenViewModel.catalogDescriptor
          ?: return@PostsScreenLocalSearchToolbar

        val globalSearchScreen = ComposeScreen.createScreen<GlobalSearchScreen>(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          args = {
            putParcelable(GlobalSearchScreen.CATALOG_DESCRIPTOR, descriptor)
            putString(GlobalSearchScreen.SEARCH_QUERY, currentQuery)
          },
          callbacks = {
            callback<PostDescriptor>(
              callbackKey = GlobalSearchScreen.ON_POST_CLICKED,
              func = { postDescriptor ->
                globalUiInfoManager.updateCurrentPage(
                  screenKey = ThreadScreen.SCREEN_KEY,
                  animate = true
                )
                threadScreenViewModel.loadThread(
                  threadDescriptor = postDescriptor.threadDescriptor,
                  loadOptions = PostScreenViewModel.LoadOptions(
                    forced = true,
                    scrollToPost = postDescriptor
                  )
                )
              }
            )

            callback(
              callbackKey = GlobalSearchScreen.CLOSE_CATALOG_SEARCH_TOOLBAR,
              func = {
                if (localSearchToolbar.searchQuery.isNotEmpty()) {
                  return@callback
                }

                kurobaToolbarContainerState.popToolbar(
                  expectedKey = localSearchToolbar.toolbarKey,
                  withAnimation = false
                )
              }
            )
          }
        )

        navigationRouter.pushScreen(globalSearchScreen)
      },
      showFoundPostsInPopup = { foundPostDescriptors ->
        val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
          ?: return@PostsScreenLocalSearchToolbar

        showFoundPostsInPopup(catalogDescriptor, foundPostDescriptors)
      },
      closeSearch = { toolbarKey -> kurobaToolbarContainerState.popToolbar(toolbarKey) }
    )
  }

  private suspend fun floatingMenuItems(): List<FloatingMenuItem> {
    val menuItems = mutableListOf<FloatingMenuItem>()

    menuItems += FloatingMenuItem.Text(
      menuItemKey = CatalogScreenToolbarActionHandler.ToolbarMenuItems.Reload,
      text = FloatingMenuItem.MenuItemText.Id(R.string.reload)
    )
    menuItems += FloatingMenuItem.Text(
      menuItemKey = CatalogScreenToolbarActionHandler.ToolbarMenuItems.Album,
      text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_album)
    )
    menuItems += kotlin.run {
      val catalogPostViewMode = appSettings.catalogPostViewMode.read().toPostViewMode()

      return@run FloatingMenuItem.NestedItems(
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_catalog_post_view_mode),
        moreItems = listOf(
          FloatingMenuItem.Group(
            checkedMenuItemKey = catalogPostViewMode,
            groupItems = listOf(
              FloatingMenuItem.Text(
                menuItemKey = PostViewMode.List,
                text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_catalog_list_view_mode)
              ),
              FloatingMenuItem.Text(
                menuItemKey = PostViewMode.Grid,
                text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_catalog_grid_view_mode)
              ),
            )
          )
        )
      )
    }
    menuItems += kotlin.run {
      val catalogGridModeColumnCount = appSettings.catalogGridModeColumnCount.read()
      val checkedMenuItemKey = CatalogScreenToolbarActionHandler.CatalogGridModeColumnCountOption(catalogGridModeColumnCount)

      return@run FloatingMenuItem.NestedItems(
        text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_catalog_grid_mode_column_count),
        moreItems = listOf(
          FloatingMenuItem.Group(
            checkedMenuItemKey = checkedMenuItemKey,
            groupItems = (AppSettings.CATALOG_MIN_COLUMN_COUNT until AppSettings.CATALOG_MAX_COLUMN_COUNT).map { columnCount ->
              val option = CatalogScreenToolbarActionHandler.CatalogGridModeColumnCountOption(columnCount)

              val text = if (columnCount == 0) {
                appResources.string(R.string.catalog_toolbar_catalog_grid_mode_column_count_auto)
              } else {
                appResources.string(R.string.catalog_toolbar_catalog_grid_mode_column_count_n, columnCount)
              }

              return@map FloatingMenuItem.Text(
                menuItemKey = option,
                text = FloatingMenuItem.MenuItemText.String(text)
              )
            }
          )
        )
      )
    }
    menuItems += FloatingMenuItem.Text(
      menuItemKey = CatalogScreenToolbarActionHandler.ToolbarMenuItems.OpenThreadByIdentifier,
      text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_open_thread_by_identifier),
    )
    menuItems += FloatingMenuItem.Text(
      menuItemKey = CatalogScreenToolbarActionHandler.ToolbarMenuItems.CatalogDevMenu,
      text = FloatingMenuItem.MenuItemText.Id(R.string.catalog_toolbar_dev_menu),
      visible = androidHelpers.isDevFlavor()
    )
    menuItems += FloatingMenuItem.Footer(
      items = listOf(
        FloatingMenuItem.Icon(
          menuItemKey = CatalogScreenToolbarActionHandler.ToolbarMenuItems.ScrollTop,
          iconId = R.drawable.ic_baseline_arrow_upward_24
        ),
        FloatingMenuItem.Icon(
          menuItemKey = CatalogScreenToolbarActionHandler.ToolbarMenuItems.ScrollBottom,
          iconId = R.drawable.ic_baseline_arrow_downward_24
        )
      )
    )

    return menuItems
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = true
  override val screenContentLoadedFlow: StateFlow<Boolean> = catalogScreenViewModel.postScreenState.contentLoaded

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
            currentPage.screenKey == screenKey || currentPage.screenKey == ThreadScreen.SCREEN_KEY
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

      return@HandleBackPresses false
    }

    ProcessCaptchaRequestEvents(
      currentChanDescriptorProvider = { catalogScreenViewModel.catalogDescriptor },
      componentActivityProvider = { componentActivity },
      navigationRouterProvider = { navigationRouter }
    )

    val screenContentLoaded by screenContentLoadedFlow.collectAsState()
    val currentCatalogDescriptor by threadScreenViewModel.currentlyOpenedCatalogFlow.collectAsState()

    LaunchedEffect(
      key1 = currentCatalogDescriptor,
      block = {
        val replyLayoutState = replyLayoutViewModel.getOrCreateReplyLayoutState(currentCatalogDescriptor)

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

    Box(modifier = Modifier.fillMaxSize()) {
      CatalogPostListScreen(
        screenContentLoaded = screenContentLoaded,
        screenKey = screenKey,
        isCatalogScreen = isCatalogScreen,
        replyLayoutStateProvider = { replyLayoutState },
        postLongtapContentMenuProvider = { postLongtapContentMenu },
        onPostImageClicked = { chanDescriptor, postImageDataResult, thumbnailBoundsInRoot ->
          val postImageData = if (postImageDataResult.isFailure) {
            snackbarManager.errorToast(
              message = postImageDataResult.exceptionOrThrow().errorMessageOrClassName(userReadable = true),
              screenKey = screenKey
            )

            return@CatalogPostListScreen
          } else {
            postImageDataResult.getOrThrow()
          }

          val catalogDescriptor = chanDescriptor as CatalogDescriptor

          clickedThumbnailBoundsStorage.storeBounds(postImageData, thumbnailBoundsInRoot)

          val mediaViewerScreen = ComposeScreen.createScreen<MediaViewerScreen>(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            args = {
              val mediaViewerParams = MediaViewerParams.Catalog(
                chanDescriptor = catalogDescriptor,
                initialImageUrlString = postImageData.fullImageAsString
              )

              putParcelable(MediaViewerScreen.mediaViewerParamsKey, mediaViewerParams)
              putParcelable(MediaViewerScreen.openedFromScreenKey, screenKey)
            }
          )

          navigationRouter.presentScreen(mediaViewerScreen)
        },
        postListSearchButtons = {
          PostListSearchButtons(
            postsScreenViewModelProvider = { catalogScreenViewModel },
            searchToolbarProvider = { localSearchToolbar },
            kurobaToolbarContainerStateProvider = { kurobaToolbarContainerState }
          )
        }
      )
    }
  }

  companion object {
    private const val TAG = "CatalogScreen"
    val SCREEN_KEY = ScreenKey("CatalogScreen")
  }

}


@Composable
private fun BoxScope.CatalogPostListScreen(
  screenContentLoaded: Boolean,
  screenKey: ScreenKey,
  isCatalogScreen: Boolean,
  replyLayoutStateProvider: () -> IReplyLayoutState,
  postLongtapContentMenuProvider: () -> PostLongtapContentMenu,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  postListSearchButtons: @Composable () -> Unit
) {
  val catalogScreenViewModel = koinRememberViewModel<CatalogScreenViewModel>()
  val threadScreenViewModel = koinRememberViewModel<ThreadScreenViewModel>()
  val historyScreenViewModel = koinRememberViewModel<HistoryScreenViewModel>()
  val bookmarksScreenViewModel = koinRememberViewModel<BookmarksScreenViewModel>()
  val homeScreenViewModel = koinRememberViewModel<HomeScreenViewModel>()
  val snackbarManager = koinRemember<SnackbarManager>()
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val chanThreadManager = koinRemember<ChanThreadManager>()

  val windowInsets = LocalWindowInsets.current
  val orientation = LocalConfiguration.current.orientation
  val mainUiLayoutMode = LocalMainUiLayoutMode.current

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val fabSize = dimensionResource(id = R.dimen.fab_size)
  val fabVertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)
  var replyLayoutContainerHeight by remember { mutableStateOf(0.dp) }

  val kurobaSnackbarState = rememberKurobaSnackbarState()
  val postCellCommentTextSizeSp by globalUiInfoManager.postCellCommentTextSizeSp.collectAsState()
  val postCellSubjectTextSizeSp by globalUiInfoManager.postCellSubjectTextSizeSp.collectAsState()
  val replyLayoutVisibilityInfoStateForScreen by globalUiInfoManager.replyLayoutVisibilityInfoStateForScreen(screenKey)
  val catalogPostViewMode by globalUiInfoManager.catalogPostViewMode.collectAsState()

  val postListOptions by remember(
    windowInsets,
    replyLayoutVisibilityInfoStateForScreen,
    replyLayoutContainerHeight,
    catalogPostViewMode
  ) {
    derivedStateOf {
      val bottomPadding = when (replyLayoutVisibilityInfoStateForScreen) {
        ReplyLayoutVisibility.Collapsed -> windowInsets.bottom
        ReplyLayoutVisibility.Opened,
        ReplyLayoutVisibility.Expanded -> windowInsets.bottom + replyLayoutContainerHeight
      }

      return@derivedStateOf PostListOptions(
        isCatalogMode = isCatalogScreen,
        showThreadStatusCell = false,
        textSelectionEnabled = false,
        isInPopup = false,
        pullToRefreshEnabled = true,
        openedFromScreenKey = screenKey,
        contentPadding = PaddingValues(
          top = toolbarHeight + windowInsets.top,
          bottom = bottomPadding + fabSize + fabVertOffset
        ),
        mainUiLayoutMode = mainUiLayoutMode,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        detectLinkableClicks = true,
        orientation = orientation,
        postViewMode = catalogPostViewMode
      )
    }
  }

  PostListContent(
    modifier = Modifier.fillMaxSize(),
    postListOptions = postListOptions,
    postsScreenViewModelProvider = { catalogScreenViewModel },
    onPostCellClicked = { postCellData ->
      globalUiInfoManager.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)

      val threadDescriptor = ThreadDescriptor(
        catalogDescriptor = postCellData.postDescriptor.catalogDescriptor,
        threadNo = postCellData.postNo
      )

      threadScreenViewModel.loadThread(threadDescriptor)
    },
    onPostCellLongClicked = { postCellData ->
      postLongtapContentMenuProvider().showMenu(
        postListOptions = postListOptions,
        postCellData = postCellData,
        reparsePostsFunc = { postDescriptors ->
          val catalogDescriptor = catalogScreenViewModel.catalogDescriptor
          if (catalogDescriptor == null) {
            return@showMenu
          }

          catalogScreenViewModel.reparsePostsByDescriptors(
            chanDescriptor = catalogDescriptor,
            postDescriptors = postDescriptors
          )
        }
      )
    },
    onLinkableClicked = { postCellData, linkable ->
      // no-op (for now?)
    },
    onLinkableLongClicked = { postCellData, linkable ->
      // no-op (for now?)
    },
    onPostRepliesClicked = { chanDescriptor, postDescriptor ->
      // no-op
    },
    onCopySelectedText = {
      // no-op
    },
    onQuoteSelectedText = { _, _, _ ->
      // no-op
    },
    onPostImageClicked = onPostImageClicked,
    onGoToPostClicked = null,
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

  if (mainUiLayoutMode == MainUiLayoutMode.Split) {
    val lastLoadError by catalogScreenViewModel.postScreenState.lastLoadErrorState.collectAsState()
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
    chanDescriptor = catalogScreenViewModel.catalogDescriptor,
    replyLayoutState = replyLayoutStateProvider(),
    onReplayLayoutHeightChanged = { newHeightDp -> replyLayoutContainerHeight = newHeightDp },
    onAttachedMediaClicked = { attachedMedia ->
      // TODO(KurobaEx): show options
      snackbarManager.toast(
        message = "Media editor is not implemented yet",
        screenKey = CatalogScreen.SCREEN_KEY
      )
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