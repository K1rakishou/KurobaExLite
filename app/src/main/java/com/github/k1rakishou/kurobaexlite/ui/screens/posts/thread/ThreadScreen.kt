package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.home.LocalMainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostsScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostsScreenFloatingActionButton
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list.PostListOptions
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class ThreadScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val parsedPostDataCache: ParsedPostDataCache by inject(ParsedPostDataCache::class.java)

  private val threadScreenToolbarActionHandler by lazy {
    ThreadScreenToolbarActionHandler()
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
        text = FloatingMenuItem.MenuItemText.Id(R.string.thread_screen_action_copy_thread_url)
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
    val mainUiLayoutMode = LocalMainUiLayoutMode.current

    val kurobaToolbarState = remember(key1 = mainUiLayoutMode) {
      val leftIconInfo = when (mainUiLayoutMode) {
        MainUiLayoutMode.Portrait -> LeftIconInfo(R.drawable.ic_baseline_arrow_back_24)
        MainUiLayoutMode.Split -> null
      }

      return@remember KurobaToolbarState(
        leftIconInfo = leftIconInfo,
        middlePartInfo = MiddlePartInfo(centerContent = false),
        postScreenToolbarInfo = PostScreenToolbarInfo(isCatalogScreen = false)
      )
    }

    UpdateToolbarTitle(
      parsedPostDataCache = parsedPostDataCache,
      postScreenState = threadScreenViewModel.postScreenState,
      kurobaToolbarState = kurobaToolbarState
    )

    KurobaToolbar(
      screenKey = screenKey,
      componentActivity = componentActivity,
      kurobaToolbarState = kurobaToolbarState,
      navigationRouter = navigationRouter,
      canProcessBackEvent = { canProcessBackEvent(mainUiLayoutMode, uiInfoManager.currentPage) },
      onLeftIconClicked = { uiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY) },
      onMiddleMenuClicked = null,
      onSearchQueryUpdated = { searchQuery ->
        uiInfoManager.onChildScreenSearchStateChanged(screenKey, searchQuery)
        threadScreenViewModel.updateSearchQuery(searchQuery)
      },
      onToolbarSortClicked = null,
      onToolbarOverflowMenuClicked = {
        navigationRouter.presentScreen(
          FloatingMenuScreen(
            floatingMenuKey = FloatingMenuScreen.THREAD_OVERFLOW,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            menuItems = floatingMenuItems,
            onMenuItemClicked = { menuItem ->
              threadScreenToolbarActionHandler.processClickedToolbarMenuItem(
                menuItem = menuItem,
                threadScreenViewModelProvider = { threadScreenViewModel }
              )
            }
          )
        )
      })
  }

  @Composable
  override fun Content() {
    RouterHost(
      screenKey = screenKey,
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
    val configuration = LocalConfiguration.current
    val windowInsets = LocalWindowInsets.current
    val context = LocalContext.current
    val mainUiLayoutMode = LocalMainUiLayoutMode.current

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
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
            bottom = windowInsets.bottom + fabVertOffset
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
      postsScreenViewModel = threadScreenViewModel,
      onPostCellClicked = { postData ->
        // TODO(KurobaEx):
      },
      onLinkableClicked = { postData, linkable ->
        processClickedLinkable(context, linkable)
      },
      onPostRepliesClicked = { postDescriptor ->
        showRepliesForPost(PopupRepliesScreen.ReplyViewMode.RepliesFrom(postDescriptor))
      },
      onPostImageClicked = { chanDescriptor, postImageData ->
        val threadDescriptor = chanDescriptor as ThreadDescriptor

        val mediaViewerScreen = MediaViewerScreen(
          mediaViewerParams = MediaViewerParams.Thread(
            threadDescriptor = threadDescriptor,
            initialImageUrl = postImageData.fullImageAsUrl
          ),
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
      uiInfoManager = uiInfoManager,
      snackbarManager = snackbarManager,
      kurobaSnackbarState = kurobaSnackbarState
    )
  }

  private fun processClickedLinkable(
    context: Context,
    linkable: PostCommentParser.TextPartSpan.Linkable
  ) {
    when (linkable) {
      is PostCommentParser.TextPartSpan.Linkable.Quote -> {
        if (linkable.crossThread) {
          val postDescriptorReadable = linkable.postDescriptor.asReadableString()

          navigationRouter.presentScreen(
            DialogScreen(
              dialogKey = DialogScreen.OPEN_EXTERNAL_THREAD,
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              params = DialogScreen.Params(
                title = DialogScreen.Text.Id(R.string.thread_screen_open_external_thread_dialog_title),
                description = DialogScreen.Text.String(
                  context.resources.getString(
                    R.string.thread_screen_open_external_thread_dialog_description,
                    postDescriptorReadable
                  )
                ),
                negativeButton = DialogScreen.cancelButton(),
                positiveButton = DialogScreen.okButton {
                  threadScreenViewModel.loadThread(
                    threadDescriptor = linkable.postDescriptor.threadDescriptor,
                    loadOptions = PostScreenViewModel.LoadOptions(forced = true)
                  )
                }
              )
            )
          )

          return
        }

        val replyTo = PopupRepliesScreen.ReplyViewMode.ReplyTo(linkable.postDescriptor)
        showRepliesForPost(replyTo)
      }
      is PostCommentParser.TextPartSpan.Linkable.Board -> {
        // TODO()
      }
      is PostCommentParser.TextPartSpan.Linkable.Search -> {
        // TODO()
      }
      is PostCommentParser.TextPartSpan.Linkable.Url -> {
        // TODO()
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}