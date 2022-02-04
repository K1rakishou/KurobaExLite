package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.PostsScreenToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class ThreadScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean
) : PostsScreen(componentActivity, navigationRouter, isStartScreen) {
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  private val threadScreenToolbarActionHandler by lazy {
    ThreadScreenToolbarActionHandler(threadScreenViewModel)
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = false

  private val toolbarMenuItems: List<ToolbarMenuItem> by lazy {
    listOf(
      ToolbarMenuItem.TextMenu(
        menuItemId = ThreadScreenToolbarActionHandler.ACTION_RELOAD,
        textId = R.string.reload
      )
    )
  }

  @Composable
  override fun postDataAsync(): AsyncData<List<State<PostData>>> {
    return threadScreenViewModel.postScreenState.postDataAsyncState()
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    with(boxScope) {
      PostsScreenToolbar(
        isCatalogScreen = isCatalogScreen,
        postListAsync = postDataAsync(),
        onToolbarOverflowMenuClicked = {
          navigationRouter.presentScreen(
            FloatingMenuScreen(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              menuItems = toolbarMenuItems,
              onMenuItemClicked = { menuItem ->
                threadScreenToolbarActionHandler.processClickedToolbarMenuItem(menuItem)
              }
            )
          )
        })
    }
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
    Box(modifier = Modifier.fillMaxSize()) {
      ThreadPostListScreen()

      val parsingPosts by threadScreenViewModel.parsingPostsAsync
      if (parsingPosts) {
        CatalogOrThreadLoadingIndicator()
      }
    }
  }

  @Composable
  private fun ThreadPostListScreen() {
    PostListContent(
      isCatalogMode = isCatalogScreen,
      mainUiLayoutMode = uiInfoManager.mainUiLayoutMode(),
      postsScreenViewModel = threadScreenViewModel,
      onPostCellClicked = { postData ->
        // TODO(KurobaEx):
      }
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}