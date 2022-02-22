package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadPostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenState

class PopupRepliesScreenViewModel(
  private val chanThreadCache: ChanThreadCache,
  private val postReplyChainManager: PostReplyChainManager,
  application: KurobaExLiteApplication,
  globalConstants: GlobalConstants,
  themeEngine: ThemeEngine
) : PostScreenViewModel(application, globalConstants, themeEngine) {
  private val threadScreenState = ThreadScreenState()
  private val postReplyChainStack = mutableListOf<PopupRepliesScreen.ReplyViewMode>()

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload() {
    // no-op
  }

  override fun refresh() {
    // no-op
  }

  suspend fun loadRepliesForMode(
    replyViewMode: PopupRepliesScreen.ReplyViewMode,
    isPushing: Boolean = true
  ): Boolean {
    if (isPushing) {
      val indexOfExisting = postReplyChainStack.indexOfFirst { it == replyViewMode }
      if (indexOfExisting >= 0) {
        // Move on top of the stack (or should we even do this?)
        postReplyChainStack += postReplyChainStack.removeAt(indexOfExisting)
      } else {
        postReplyChainStack += replyViewMode
      }
    }

    return when (replyViewMode) {
      is PopupRepliesScreen.ReplyViewMode.ReplyTo -> loadReplyTo(replyViewMode)
      is PopupRepliesScreen.ReplyViewMode.RepliesFrom -> loadRepliesFrom(replyViewMode)
    }
  }

  suspend fun popReplyChain(): Boolean {
    postReplyChainStack.removeLastOrNull()
      ?: return false

    val prevMode = postReplyChainStack.lastOrNull()
      ?: return false

    return loadRepliesForMode(replyViewMode = prevMode, isPushing = false)
  }

  private suspend fun loadRepliesFrom(replyViewMode: PopupRepliesScreen.ReplyViewMode.RepliesFrom): Boolean {
    val postDescriptor = replyViewMode.postDescriptor
    val repliesFrom = postReplyChainManager.getRepliesFrom(postDescriptor)
    if (repliesFrom.isEmpty()) {
      postScreenState.postsAsyncDataState.value = AsyncData.Empty
      return false
    }

    val posts = chanThreadCache.getManyForDescriptor(postDescriptor.threadDescriptor, repliesFrom)
    if (posts.isEmpty()) {
      postScreenState.postsAsyncDataState.value = AsyncData.Empty
      return false
    }

    val threadPostsState = ThreadPostsState(
      threadDescriptor = postDescriptor.threadDescriptor,
      threadPosts = posts
    )

    postScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
    return true
  }

  private suspend fun loadReplyTo(replyViewMode: PopupRepliesScreen.ReplyViewMode.ReplyTo): Boolean {
    val postDescriptor = replyViewMode.postDescriptor

    val post = chanThreadCache.getPost(postDescriptor)
    if (post == null) {
      postScreenState.postsAsyncDataState.value = AsyncData.Empty
      return false
    }

    val threadPostsState = ThreadPostsState(
      threadDescriptor = postDescriptor.threadDescriptor,
      threadPosts = listOf(post)
    )

    postScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
    return true
  }

}