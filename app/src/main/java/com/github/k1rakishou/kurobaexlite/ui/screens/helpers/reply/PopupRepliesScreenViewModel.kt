package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadPostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenState
import kotlinx.coroutines.CompletableDeferred

class PopupRepliesScreenViewModel(
  private val chanThreadCache: ChanThreadCache,
  application: KurobaExLiteApplication,
  globalConstants: GlobalConstants,
  themeEngine: ThemeEngine
) : PostScreenViewModel(application, globalConstants, themeEngine) {
  private val threadScreenState = ThreadScreenState()

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload() {
    // no-op
  }

  override fun refresh() {
    // no-op
  }

  suspend fun loadRepliesForPost(postDescriptor: PostDescriptor) {
    postListBuilt = CompletableDeferred()

    val post = chanThreadCache.getPost(postDescriptor)
    if (post == null) {
      postScreenState.postsAsyncDataState.value = AsyncData.Empty
    } else {
      val threadPostsState = ThreadPostsState(
        threadDescriptor = postDescriptor.threadDescriptor,
        threadPosts = listOf(post)
      )

      postScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
    }
  }

}