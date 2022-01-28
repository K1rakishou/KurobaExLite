package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.PostsScreenToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class ThreadScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean
) : PostsScreen(componentActivity, navigationRouter, isStartScreen) {
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = false

  private val toolbarMenuItems: List<ToolbarMenuItem> by lazy {
    listOf()
  }

  @Composable
  override fun postDataAsync(): AsyncData<List<PostData>> {
    return threadScreenViewModel.postScreenState.postDataAsync()
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
              menuItems = toolbarMenuItems
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
      mainUiLayoutMode = globalConstants.mainUiLayoutMode(),
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