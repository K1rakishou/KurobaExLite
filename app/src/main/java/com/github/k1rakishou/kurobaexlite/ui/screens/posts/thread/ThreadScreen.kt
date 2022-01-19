package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import org.koin.androidx.viewmodel.ext.android.viewModel

class ThreadScreen(componentActivity: ComponentActivity) : ComposeScreen(componentActivity) {
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    PostListContent(
      isCatalogMode = true,
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