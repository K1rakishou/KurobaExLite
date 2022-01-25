package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import org.koin.androidx.viewmodel.ext.android.viewModel

class ThreadScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    RouterHost(
      navigationRouter = navigationRouter,
      defaultScreen = {
        PostListContent(
          isCatalogMode = true,
          postsScreenViewModel = threadScreenViewModel,
          onPostCellClicked = { postData ->
            // TODO(KurobaEx):
          }
        )
      }
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}