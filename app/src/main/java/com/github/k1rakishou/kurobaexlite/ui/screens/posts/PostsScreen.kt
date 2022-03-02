package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply.PopupRepliesScreen

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
    postListAsync: AsyncData<AbstractPostsState>,
    kurobaToolbarState: KurobaToolbarState
  ) {
    when (postListAsync) {
      AsyncData.Empty -> {
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
        LaunchedEffect(
          key1 = isCatalogScreen,
          key2 = postListAsync,
          block = {
            val postListState = postListAsync.data.posts.firstOrNull()
              ?: return@LaunchedEffect

            val originalPost by postListState
            val chanDescriptor = postListAsync.data.chanDescriptor

            val title = parsedPostDataCache.formatToolbarTitle(
              chanDescriptor = chanDescriptor,
              postDescriptor = originalPost.postDescriptor,
              catalogMode = isCatalogScreen
            )

            if (title == null) {
              return@LaunchedEffect
            }

            kurobaToolbarState.toolbarTitleState.value = title
          })
      }
    }
  }

}