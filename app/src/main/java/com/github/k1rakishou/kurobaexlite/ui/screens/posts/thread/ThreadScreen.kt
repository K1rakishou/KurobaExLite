package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.KurobaSnackbar
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.rememberKurobaSnackbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.*
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.LocalMainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListOptions
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreen
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
    ThreadScreenToolbarActionHandler(threadScreenViewModel)
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = false

  private val floatingMenuItems: List<FloatingMenuItem> by lazy {
    listOf(
      FloatingMenuItem.Text(
        menuItemKey = ThreadScreenToolbarActionHandler.ACTION_RELOAD,
        text = FloatingMenuItem.MenuItemText.Id(R.string.reload)
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
    val postListAsync by threadScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
    val mainUiLayoutMode = LocalMainUiLayoutMode.current

    val kurobaToolbarState = remember {
      val leftIconInfo = when (mainUiLayoutMode) {
        MainUiLayoutMode.Portrait -> LeftIconInfo(R.drawable.ic_baseline_arrow_back_24)
        MainUiLayoutMode.Split -> null
      }

      val middlePartInfo = MiddlePartInfo(centerContent = false)

      return@remember KurobaToolbarState(
        leftIconInfo = leftIconInfo,
        middlePartInfo = middlePartInfo,
        postScreenToolbarInfo = PostScreenToolbarInfo(isCatalogScreen = false)
      )
    }

    UpdateCatalogToolbarTitle(
      parsedPostDataCache = parsedPostDataCache,
      postListAsync = postListAsync,
      kurobaToolbarState = kurobaToolbarState
    )

    KurobaToolbar(
      kurobaToolbarState = kurobaToolbarState,
      navigationRouter = navigationRouter,
      onLeftIconClicked = { homeScreenViewModel.updateCurrentPage(CatalogScreen.SCREEN_KEY) },
      onMiddleMenuClicked = null,
      onSearchQueryUpdated = { searchQuery -> threadScreenViewModel.updateSearchQuery(searchQuery) },
      onToolbarOverflowMenuClicked = {
        navigationRouter.presentScreen(
          FloatingMenuScreen(
            floatingMenuKey = FloatingMenuScreen.THREAD_OVERFLOW,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            menuItems = floatingMenuItems,
            onMenuItemClicked = { menuItem ->
              threadScreenToolbarActionHandler.processClickedToolbarMenuItem(menuItem)
            }
          )
        )
      })
  }

  @Composable
  override fun Content() {
    RouterHost(
      navigationRouter = navigationRouter,
      defaultScreen = { ThreadPostListScreenContent() }
    )
  }

  @Composable
  private fun ThreadPostListScreenContent() {
    val kurobaSnackbarState = rememberKurobaSnackbarState()

    Box(modifier = Modifier.fillMaxSize()) {
      ThreadPostListScreen()

      KurobaSnackbar(
        modifier = Modifier.fillMaxSize(),
        kurobaSnackbarState = kurobaSnackbarState,
        snackbarEventFlow = threadScreenViewModel.snackbarEventFlow
      )
    }
  }

  @Composable
  private fun ThreadPostListScreen() {
    val configuration = LocalConfiguration.current
    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    val postListOptions = remember {
      PostListOptions(
        isCatalogMode = isCatalogScreen,
        isInPopup = false,
        contentPadding = PaddingValues(
          top = toolbarHeight + windowInsets.top,
          bottom = windowInsets.bottom
        ),
        mainUiLayoutMode = uiInfoManager.mainUiLayoutMode(configuration)
      )
    }

    PostListContent(
      modifier = Modifier.fillMaxSize(),
      postListOptions = postListOptions,
      postsScreenViewModel = threadScreenViewModel,
      onPostCellClicked = { postData ->
        // TODO(KurobaEx):
      },
      onLinkableClicked = { linkable ->
        processClickedLinkable(linkable)
      },
      onPostRepliesClicked = { postDescriptor ->
        showRepliesForPost(PopupRepliesScreen.ReplyViewMode.RepliesFrom(postDescriptor))
      },
      onPostListScrolled = { delta ->
        homeScreenViewModel.onChildContentScrolling(delta)
      },
      onPostListTouchingTopOrBottomStateChanged = { touchingBottom ->
        homeScreenViewModel.onPostListTouchingTopOrBottomStateChanged(touchingBottom)
      },
      onPostListDragStateChanged = { dragging ->
        homeScreenViewModel.onPostListDragStateChanged(dragging)
      },
      onFastScrollerDragStateChanged = { dragging ->
        homeScreenViewModel.onFastScrollerDragStateChanged(dragging)
      }
    )
  }

  private fun processClickedLinkable(linkable: PostCommentParser.TextPartSpan.Linkable) {
    when (linkable) {
      is PostCommentParser.TextPartSpan.Linkable.Quote -> {
        showRepliesForPost(PopupRepliesScreen.ReplyViewMode.ReplyTo(linkable.postDescriptor))
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