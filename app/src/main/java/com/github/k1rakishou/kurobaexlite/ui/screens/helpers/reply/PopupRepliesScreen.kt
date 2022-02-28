package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListOptions
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PopupRepliesScreen(
  private val replyViewMode: ReplyViewMode,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val popupRepliesScreenViewModel: PopupRepliesScreenViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    val postListOptions = remember {
      PostListOptions(
        isCatalogMode = false,
        isInPopup = true,
        contentPadding = PaddingValues(),
        mainUiLayoutMode = MainUiLayoutMode.Portrait
      )
    }
    val coroutineScope = rememberCoroutineScope()

    PostListContent(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      postListOptions = postListOptions,
      postsScreenViewModel = popupRepliesScreenViewModel,
      onPostCellClicked = { postData ->
      },
      onLinkableClicked = { postData, linkable ->
        coroutineScope.launch { processClickedLinkable(linkable) }
      },
      onPostRepliesClicked = { postDescriptor ->
        coroutineScope.launch {
          popupRepliesScreenViewModel.loadRepliesForMode(ReplyViewMode.RepliesFrom(postDescriptor))
        }
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
      key1 = replyViewMode,
      block = { popupRepliesScreenViewModel.loadRepliesForMode(replyViewMode) }
    )
  }

  private suspend fun processClickedLinkable(
    linkable: PostCommentParser.TextPartSpan.Linkable
  ) {
    when (linkable) {
      is PostCommentParser.TextPartSpan.Linkable.Quote -> {
        val replyTo = ReplyViewMode.ReplyTo(linkable.postDescriptor)
        popupRepliesScreenViewModel.loadRepliesForMode(replyTo)
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

  override suspend fun onBackPressed(): Boolean {
    if (popupRepliesScreenViewModel.popReplyChain()) {
      return true
    }

    return super.onBackPressed()
  }

  sealed class ReplyViewMode {
    data class ReplyTo(
      val postDescriptor: PostDescriptor
    ) : ReplyViewMode()

    data class RepliesFrom(
      val postDescriptor: PostDescriptor
    ) : ReplyViewMode()
  }

  companion object {
    private val SCREEN_KEY = ScreenKey("PopupRepliesScreen")
  }

}