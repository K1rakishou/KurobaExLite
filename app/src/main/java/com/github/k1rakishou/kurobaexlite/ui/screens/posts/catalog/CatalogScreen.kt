package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class CatalogScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : PostsScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    LaunchedEffect(key1 = Unit, block = { catalogScreenViewModel.loadCatalog() })

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
        CatalogOrThreadLoadingIndicator(isCatalogMode = true)
      }
    }
  }

  @Composable
  private fun CatalogPostListScreen() {
    PostListContent(
      isCatalogMode = false,
      postsScreenViewModel = catalogScreenViewModel,
      onPostCellClicked = { postData ->
        homeScreenViewModel.updateCurrentPage(
          screenKey = ThreadScreen.SCREEN_KEY,
          animate = true
        )

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
  }
}