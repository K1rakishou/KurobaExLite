package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.screens.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.*
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class CatalogScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean
) : PostsScreen(componentActivity, navigationRouter, isStartScreen) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  private val toolbarMenuItems: List<ToolbarMenuItem> by lazy {
    listOf(
      ToolbarMenuItem.TextMenu(
        id = ACTION_OPEN_THREAD_BY_IDENTIFIER,
        textId = R.string.catalog_toolbar_open_thread_by_identifier
      )
    )
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val isCatalogScreen: Boolean = true

  @Composable
  override fun postDataAsync(): AsyncData<List<PostData>> {
    return catalogScreenViewModel.postScreenState.postDataAsync()
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
        }
      )
    }
  }

  @Composable
  override fun Content() {
    LaunchedEffect(
      key1 = Unit,
      block = { catalogScreenViewModel.loadCatalog() }
    )

    RouterHost(
      navigationRouter = navigationRouter,
      defaultScreen = { CatalogPostListScreenContent() }
    )
  }

  @Composable
  private fun CatalogPostListScreenContent() {
    Box(modifier = Modifier.fillMaxSize()) {
      CatalogPostListScreen()

      val parsingPosts by catalogScreenViewModel.parsingPostsAsync
      if (parsingPosts) {
        CatalogOrThreadLoadingIndicator()
      }
    }
  }

  @Composable
  private fun CatalogPostListScreen() {
    PostListContent(
      isCatalogMode = isCatalogScreen,
      postsScreenViewModel = catalogScreenViewModel,
      onPostCellClicked = { postData ->
        homeScreenViewModel.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY,)

        val threadDescriptor = ThreadDescriptor(
          catalogDescriptor = postData.postDescriptor.catalogDescriptor,
          threadNo = postData.postNo
        )

        threadScreenViewModel.loadThreadFromCatalog(threadDescriptor)
      }
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("CatalogScreen")

    private const val ACTION_OPEN_THREAD_BY_IDENTIFIER = 0
  }

}