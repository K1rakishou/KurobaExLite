package com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply

import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadPostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class PopupRepliesScreenViewModel(
  private val chanThreadCache: ChanThreadCache,
  private val postReplyChainManager: PostReplyChainManager,
  application: KurobaExLiteApplication,
  globalConstants: GlobalConstants,
  themeEngine: ThemeEngine,
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(application, globalConstants, themeEngine, savedStateHandle) {
  private val parsedPostDataCache by inject<ParsedPostDataCache>(ParsedPostDataCache::class.java)

  private val threadScreenState = ThreadScreenState()
  private val postReplyChainStack = mutableListOf<PopupRepliesScreen.ReplyViewMode>()

  private val parsedReplyToCache = LruCache<PostDescriptor, List<PostData>>(32)
  private val parsedReplyFromCache = LruCache<PostDescriptor, List<PostData>>(32)

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload(loadOptions: LoadOptions, onReloadFinished: (() -> Unit)?) {
    // no-op
  }

  override fun refresh() {
    // no-op
  }

  suspend fun loadRepliesForModeInitial(
    replyViewMode: PopupRepliesScreen.ReplyViewMode,
  ) {
    if (postReplyChainStack.isEmpty()) {
      loadRepliesForMode(replyViewMode = replyViewMode, isPushing = true)
    } else {
      val topReplyMode = postReplyChainStack.last()
      loadRepliesForMode(replyViewMode = topReplyMode, isPushing = true)
    }
  }

  suspend fun loadRepliesForMode(
    replyViewMode: PopupRepliesScreen.ReplyViewMode,
    isPushing: Boolean = true
  ): Boolean {
    if (isPushing) {
      val indexOfExisting = postReplyChainStack.indexOfFirst { it == replyViewMode }
      if (indexOfExisting >= 0) {
        // Move old on top of the stack
        postReplyChainStack.add(postReplyChainStack.removeAt(indexOfExisting))
      } else {
        // Add new on top of the stack
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

  fun clearPostReplyChainStack() {
    postReplyChainStack.clear()
  }

  private suspend fun loadRepliesFrom(replyViewMode: PopupRepliesScreen.ReplyViewMode.RepliesFrom): Boolean {
    val postDescriptor = replyViewMode.postDescriptor

    val repliesFrom = postReplyChainManager.getRepliesFrom(postDescriptor)
    if (repliesFrom.isEmpty()) {
      postScreenState.postsAsyncDataState.value = AsyncData.Empty
      return false
    }

    postScreenState.updateChanDescriptor(postDescriptor.threadDescriptor)

    val posts = chanThreadCache.getManyForDescriptor(postDescriptor.threadDescriptor, repliesFrom)
        .let { postDataList -> parsePostDataList(replyViewMode, postDescriptor, postDataList) }

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

    val threadPosts = chanThreadCache.getPost(postDescriptor)
      ?.let { postData -> parsePostData(replyViewMode, postDescriptor, postData) }

    if (threadPosts == null || threadPosts.isEmpty()) {
      postScreenState.postsAsyncDataState.value = AsyncData.Empty
      return false
    }

    val threadPostsState = ThreadPostsState(
      threadDescriptor = postDescriptor.threadDescriptor,
      threadPosts = threadPosts
    )

    postScreenState.postsAsyncDataState.value = AsyncData.Data(threadPostsState)
    return true
  }

  private suspend fun parsePostData(
    replyViewMode: PopupRepliesScreen.ReplyViewMode,
    forPostDescriptor: PostDescriptor,
    postData: PostData
  ): List<PostData> {
    return parsePostDataList(replyViewMode, forPostDescriptor, listOf(postData))
  }

  private suspend fun parsePostDataList(
    replyViewMode: PopupRepliesScreen.ReplyViewMode,
    forPostDescriptor: PostDescriptor,
    postDataList: List<PostData>
  ): List<PostData> {
    val fromCache = when (replyViewMode) {
      is PopupRepliesScreen.ReplyViewMode.RepliesFrom -> {
        parsedReplyFromCache[forPostDescriptor]
      }
      is PopupRepliesScreen.ReplyViewMode.ReplyTo -> {
        parsedReplyToCache[forPostDescriptor]
      }
    }

    if (fromCache != null) {
      return fromCache
    }

    if (postDataList.isEmpty()) {
      return emptyList()
    }

    val chanTheme = themeEngine.chanTheme

    val updatedPostDataList = withContext(Dispatchers.Default) {
      return@withContext postDataList.map { oldPostData ->
        val newParsedPostDataContext = oldPostData.parsedPostDataContext
          ?.copy(markedPostDescriptor = forPostDescriptor)
          ?: ParsedPostDataContext(isParsingCatalog = false, markedPostDescriptor = forPostDescriptor)

        val parsedPostData = parsedPostDataCache.calculateParsedPostData(
          postData = oldPostData,
          parsedPostDataContext = newParsedPostDataContext,
          chanTheme = chanTheme
        )

        return@map oldPostData.copy(parsedPostData = parsedPostData)
      }
    }

    when (replyViewMode) {
      is PopupRepliesScreen.ReplyViewMode.RepliesFrom -> {
        parsedReplyFromCache.put(forPostDescriptor, updatedPostDataList)
      }
      is PopupRepliesScreen.ReplyViewMode.ReplyTo -> {
        parsedReplyToCache.put(forPostDescriptor, updatedPostDataList)
      }
    }

    return updatedPostDataList
  }

}