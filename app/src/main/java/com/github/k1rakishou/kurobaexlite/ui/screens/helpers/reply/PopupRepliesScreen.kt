package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import org.koin.androidx.viewmodel.ext.android.viewModel

class PopupRepliesScreen(
  private val postDescriptor: PostDescriptor,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val popupRepliesScreenViewModel: PopupRepliesScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    val contentPadding = remember { PaddingValues() }

    PostListContent(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      contentPadding = contentPadding,
      isCatalogMode = false,
      mainUiLayoutMode = MainUiLayoutMode.Portrait,
      postsScreenViewModel = popupRepliesScreenViewModel,
      onPostCellClicked = { postData ->
      },
      onLinkableClicked = { linkable ->
        // no-op (for now?)
      },
      onPostRepliesClicked = { postDescriptor ->
      },
      onPostListScrolled = { delta ->
      },
      onPostListTouchingTopOrBottomStateChanged = { touching ->
      },
      onPostListDragStateChanged = { dragging ->
      },
      onFastScrollerDragStateChanged = { dragging ->
      },
    )

    LaunchedEffect(
      key1 = postDescriptor,
      block = { popupRepliesScreenViewModel.loadRepliesForPost(postDescriptor) }
    )
  }

  companion object {
    private val SCREEN_KEY = ScreenKey("PopupRepliesScreen")
  }

}