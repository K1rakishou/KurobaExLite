package com.github.k1rakishou.kurobaexlite.features.posts.reply

import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PopupPostsScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.helpers.flatMapNotNull
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PopupRepliesScreenViewModel(savedStateHandle: SavedStateHandle) : PostScreenViewModel(savedStateHandle) {
  private val threadScreenState = PopupPostsScreenState()
  private val postReplyChainStack = mutableListOf<PopupRepliesScreen.ReplyViewMode>()

  private val parsedReplyToCache = LruCache<PostDescriptor, List<PostCellData>>(32)
  private val parsedReplyFromCache = LruCache<PostDescriptor, List<PostCellData>>(32)

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload(loadOptions: LoadOptions, onReloadFinished: (() -> Unit)?) {
    error("Reloading reply popups is not supported")
  }

  override fun refresh(onRefreshFinished: (() -> Unit)?) {
    error("Refreshing reply popups is not supported")
  }

  fun collectCurrentImages(): List<PostCellImageData> {
    val replyViewMode = postReplyChainStack.lastOrNull()
      ?: return emptyList()

    val cachedPostCellDataList = when (replyViewMode) {
      is PopupRepliesScreen.ReplyViewMode.RepliesFrom -> {
        parsedReplyFromCache[replyViewMode.postDescriptor] ?: emptyList()
      }
      is PopupRepliesScreen.ReplyViewMode.ReplyTo -> {
        parsedReplyToCache[replyViewMode.postDescriptor] ?: emptyList()
      }
    }

    return cachedPostCellDataList.flatMapNotNull { postCellData -> postCellData.images }
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
    postScreenState.postsAsyncDataState.value = AsyncData.Uninitialized
    postReplyChainStack.clear()
    parsedReplyToCache.evictAll()
    parsedReplyFromCache.evictAll()
  }

  private suspend fun loadRepliesFrom(replyViewMode: PopupRepliesScreen.ReplyViewMode.RepliesFrom): Boolean {
    val postDescriptor = replyViewMode.postDescriptor
    postScreenState.updateChanDescriptor(postDescriptor.threadDescriptor)
    val repliesFrom = postReplyChainManager.getRepliesFrom(postDescriptor)

    val posts = parsePostDataList(
      replyViewMode = replyViewMode,
      forPostDescriptor = postDescriptor,
      postCellDataList = chanCache.getManyForDescriptor(postDescriptor.threadDescriptor, repliesFrom)
    )

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(postDescriptor.threadDescriptor))
    postScreenState.insertOrUpdateMany(posts)

    return true
  }

  private suspend fun loadReplyTo(replyViewMode: PopupRepliesScreen.ReplyViewMode.ReplyTo): Boolean {
    val postDescriptor = replyViewMode.postDescriptor
    postScreenState.updateChanDescriptor(postDescriptor.threadDescriptor)

    val threadPosts = chanCache.getPost(postDescriptor)
      ?.let { postData -> parsePostData(replyViewMode, postDescriptor, postData) }
      ?: emptyList()

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(postDescriptor.threadDescriptor))
    postScreenState.insertOrUpdateMany(threadPosts)

    return true
  }

  private suspend fun parsePostData(
    replyViewMode: PopupRepliesScreen.ReplyViewMode,
    forPostDescriptor: PostDescriptor,
    postData: IPostData
  ): List<PostCellData> {
    return parsePostDataList(replyViewMode, forPostDescriptor, listOf(postData))
  }

  private suspend fun parsePostDataList(
    replyViewMode: PopupRepliesScreen.ReplyViewMode,
    forPostDescriptor: PostDescriptor,
    postCellDataList: List<IPostData>
  ): List<PostCellData> {
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

    if (postCellDataList.isEmpty()) {
      return emptyList()
    }

    val chanTheme = themeEngine.chanTheme

    val updatedPostDataList = withContext(Dispatchers.Default) {
      return@withContext postCellDataList.map { oldPostData ->
        val oldParsedPostDataContext = parsedPostDataCache.getParsedPostData(
          oldPostData.postDescriptor.threadDescriptor,
          oldPostData.postDescriptor
        )?.parsedPostDataContext

        val newParsedPostDataContext = oldParsedPostDataContext
          ?.copy(highlightedPostDescriptor = forPostDescriptor)
          ?: ParsedPostDataContext(
            isParsingCatalog = false,
            highlightedPostDescriptor = forPostDescriptor
          )

        val parsedPostData = parsedPostDataCache.getOrCalculateParsedPostData(
          chanDescriptor = oldPostData.postDescriptor.threadDescriptor,
          postData = oldPostData,
          parsedPostDataContext = newParsedPostDataContext,
          chanTheme = chanTheme,
          force = true
        )

        return@map PostCellData.fromPostData(
          postData = oldPostData,
          parsedPostData = parsedPostData
        )
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