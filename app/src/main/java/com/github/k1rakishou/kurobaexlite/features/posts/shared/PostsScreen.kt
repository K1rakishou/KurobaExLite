package com.github.k1rakishou.kurobaexlite.features.posts.shared

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.ThreadScreenPostsState
import com.github.k1rakishou.kurobaexlite.helpers.unreachable
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SplitScreenLayout

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {
  abstract val isCatalogScreen: Boolean

  protected fun showRepliesForPost(replyViewMode: PopupRepliesScreen.ReplyViewMode) {
    navigationRouter.presentScreen(
      PopupRepliesScreen(
        replyViewMode = replyViewMode,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter
      )
    )
  }

  @Composable
  protected fun UpdateToolbarTitle(
    parsedPostDataCache: ParsedPostDataCache,
    postScreenState: PostScreenState,
    kurobaToolbarState: KurobaToolbarState
  ) {
    val postListAsyncMut by postScreenState.postsAsyncDataState.collectAsState()
    val postListAsync = postListAsyncMut

    when (postListAsync) {
      AsyncData.Uninitialized -> {
        if (isCatalogScreen) {
          val defaultToolbarTitle = stringResource(id = R.string.toolbar_loading_empty)
          kurobaToolbarState.toolbarTitleState.value = defaultToolbarTitle
        } else {
          kurobaToolbarState.toolbarTitleState.value = null
        }
      }
      AsyncData.Loading -> {
        kurobaToolbarState.toolbarTitleState.value = stringResource(R.string.toolbar_loading_title)
      }
      is AsyncData.Error -> {
        kurobaToolbarState.toolbarTitleState.value = stringResource(R.string.toolbar_loading_error)
      }
      is AsyncData.Data -> {
        when (val chanDescriptor = postListAsync.data.chanDescriptor) {
          is CatalogDescriptor -> {
            kurobaToolbarState.toolbarTitleState.value = parsedPostDataCache.formatCatalogToolbarTitle(chanDescriptor)
          }
          is ThreadDescriptor -> {
            val originalPost by (postScreenState as ThreadScreenPostsState).originalPostState.collectAsState()

            UpdateThreadToolbarTitle(
              threadDescriptor = chanDescriptor,
              originalPost = originalPost,
              parsedPostDataCache = parsedPostDataCache,
              kurobaToolbarState = kurobaToolbarState
            )
          }
          else -> {
            unreachable()
          }
        }
      }
    }
  }

  @Composable
  private fun UpdateThreadToolbarTitle(
    threadDescriptor: ThreadDescriptor,
    originalPost: PostCellData?,
    parsedPostDataCache: ParsedPostDataCache,
    kurobaToolbarState: KurobaToolbarState
  ) {
    LaunchedEffect(
      key1 = threadDescriptor,
      key2 = originalPost,
      block = {
        if (originalPost == null) {
          return@LaunchedEffect
        }

        parsedPostDataCache.ensurePostDataLoaded(
          isCatalog = false,
          postDescriptor = originalPost.postDescriptor,
          func = {
            val title = parsedPostDataCache.formatThreadToolbarTitle(originalPost.postDescriptor)
              ?: return@ensurePostDataLoaded

            kurobaToolbarState.toolbarTitleState.value = title
          }
        )
      })
  }

  protected fun canProcessBackEvent(
    uiLayoutMode: MainUiLayoutMode,
    currentPage: CurrentPage?
  ): Boolean {
    return when (uiLayoutMode) {
      MainUiLayoutMode.Portrait -> currentPage?.screenKey == screenKey
      MainUiLayoutMode.Split -> currentPage?.screenKey == SplitScreenLayout.SCREEN_KEY
    }
  }

}