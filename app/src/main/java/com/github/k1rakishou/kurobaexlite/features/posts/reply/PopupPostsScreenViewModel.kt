package com.github.k1rakishou.kurobaexlite.features.posts.reply

import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PopupPostsScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.helpers.sort.CatalogThreadSorter
import com.github.k1rakishou.kurobaexlite.helpers.sort.ThreadPostSorter
import com.github.k1rakishou.kurobaexlite.helpers.util.flatMapNotNull
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PopupPostsScreenViewModel(savedStateHandle: SavedStateHandle) : PostScreenViewModel(savedStateHandle) {
  private val threadScreenState = PopupPostsScreenState()
  private val postReplyChainStack = mutableListOf<PopupPostsScreen.PostViewMode>()

  private val parsedReplyToCache = LruCache<PostDescriptor, List<PostCellData>>(32)
  private val parsedReplyFromCache = LruCache<PostDescriptor, List<PostCellData>>(32)
  private val parsedPostsCache = mutableMapOf<PostDescriptor, PostCellData>()

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload(loadOptions: LoadOptions, onReloadFinished: (() -> Unit)?) {
    error("Reloading reply popups is not supported")
  }

  override fun refresh(onRefreshFinished: (() -> Unit)?) {
    error("Refreshing reply popups is not supported")
  }

  fun collectCurrentImages(): List<PostCellImageData> {
    val postViewMode = postReplyChainStack.lastOrNull()
      ?: return emptyList()

    val cachedPostCellDataList = when (postViewMode) {
      is PopupPostsScreen.PostViewMode.RepliesFrom -> {
        parsedReplyFromCache[postViewMode.postDescriptor] ?: emptyList()
      }
      is PopupPostsScreen.PostViewMode.ReplyTo -> {
        parsedReplyToCache[postViewMode.postDescriptor] ?: emptyList()
      }
      is PopupPostsScreen.PostViewMode.PostList -> {
        postViewMode.asPostDescriptorList
          .mapNotNull { postDescriptor -> parsedPostsCache[postDescriptor] }
      }
    }

    return cachedPostCellDataList.flatMapNotNull { postCellData -> postCellData.images }
  }

  suspend fun loadRepliesForModeInitial(
    postViewMode: PopupPostsScreen.PostViewMode,
  ) {
    if (postReplyChainStack.isEmpty()) {
      loadRepliesForMode(postViewMode = postViewMode, isPushing = true)
    } else {
      val topReplyMode = postReplyChainStack.last()
      loadRepliesForMode(postViewMode = topReplyMode, isPushing = true)
    }
  }

  suspend fun loadRepliesForMode(
    postViewMode: PopupPostsScreen.PostViewMode,
    isPushing: Boolean = true
  ): Boolean {
    if (isPushing) {
      val indexOfExisting = postReplyChainStack.indexOfFirst { it == postViewMode }
      if (indexOfExisting >= 0) {
        // Move old on top of the stack
        postReplyChainStack.add(postReplyChainStack.removeAt(indexOfExisting))
      } else {
        // Add new on top of the stack
        postReplyChainStack += postViewMode
      }
    }

    return when (postViewMode) {
      is PopupPostsScreen.PostViewMode.ReplyTo -> loadReplyTo(postViewMode)
      is PopupPostsScreen.PostViewMode.RepliesFrom -> loadRepliesFrom(postViewMode)
      is PopupPostsScreen.PostViewMode.PostList -> loadPostList(postViewMode)
    }
  }

  suspend fun popReplyChain(): Boolean {
    postReplyChainStack.removeLastOrNull()
      ?: return false

    val prevMode = postReplyChainStack.lastOrNull()
      ?: return false

    return loadRepliesForMode(postViewMode = prevMode, isPushing = false)
  }

  fun clearPostReplyChainStack() {
    postScreenState.postsAsyncDataState.value = AsyncData.Uninitialized
    postReplyChainStack.clear()
    parsedReplyToCache.evictAll()
    parsedReplyFromCache.evictAll()
    parsedPostsCache.clear()
  }

  private suspend fun loadPostList(postViewMode: PopupPostsScreen.PostViewMode.PostList): Boolean {
    postScreenState.updateChanDescriptor(postViewMode.chanDescriptor)

    val posts = parsePostDataList(
      postViewMode = postViewMode,
      postCellDataList = chanCache.getManyForDescriptor(
        chanDescriptor = postViewMode.chanDescriptor,
        postDescriptors = postViewMode.asPostDescriptorList
      )
    )

    val sortedPosts = when (postViewMode.chanDescriptor) {
      is CatalogDescriptor -> CatalogThreadSorter.sortCatalogPostCellData(posts, appSettings.catalogSort.read())
      is ThreadDescriptor -> ThreadPostSorter.sortThreadPostCellData(posts)
    }

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(postViewMode.chanDescriptor))
    postScreenState.insertOrUpdateMany(sortedPosts)

    return true
  }

  private suspend fun loadRepliesFrom(postViewMode: PopupPostsScreen.PostViewMode.RepliesFrom): Boolean {
    val postDescriptor = postViewMode.postDescriptor
    postScreenState.updateChanDescriptor(postDescriptor.threadDescriptor)
    val repliesFrom = postReplyChainManager.getRepliesFrom(postDescriptor)

    val posts = parsePostDataList(
      postViewMode = postViewMode,
      postCellDataList = chanCache.getManyForDescriptor(postDescriptor.threadDescriptor, repliesFrom)
    )

    val sortedPosts = ThreadPostSorter.sortThreadPostCellData(posts)

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(postDescriptor.threadDescriptor))
    postScreenState.insertOrUpdateMany(sortedPosts)

    return true
  }

  private suspend fun loadReplyTo(postViewMode: PopupPostsScreen.PostViewMode.ReplyTo): Boolean {
    val postDescriptor = postViewMode.postDescriptor
    postScreenState.updateChanDescriptor(postDescriptor.threadDescriptor)

    val threadPosts = chanCache.getThreadPost(postDescriptor)
      ?.let { postData -> parsePostDataList(postViewMode, listOf(postData)) }
      ?: emptyList()

    val sortedPosts = ThreadPostSorter.sortThreadPostCellData(threadPosts)

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(postDescriptor.threadDescriptor))
    postScreenState.insertOrUpdateMany(sortedPosts)

    return true
  }

  private suspend fun parsePostDataList(
    postViewMode: PopupPostsScreen.PostViewMode,
    postCellDataList: List<IPostData>
  ): List<PostCellData> {
    val fromCache = when (postViewMode) {
      is PopupPostsScreen.PostViewMode.RepliesFrom -> {
        parsedReplyFromCache[postViewMode.postDescriptor]
      }
      is PopupPostsScreen.PostViewMode.ReplyTo -> {
        parsedReplyToCache[postViewMode.postDescriptor]
      }
      is PopupPostsScreen.PostViewMode.PostList -> {
        postViewMode.asPostDescriptorList
          .mapNotNull { postDescriptor -> parsedPostsCache[postDescriptor] }
          .takeIf { posts -> posts.isNotEmpty() }
      }
    }

    if (fromCache != null) {
      return fromCache
    }

    if (postCellDataList.isEmpty()) {
      return emptyList()
    }

    val chanTheme = themeEngine.chanTheme

    val updatedPostDataList = withContext(Dispatchers.IO) {
      return@withContext postCellDataList.map { oldPostData ->
        val oldParsedPostDataContext = parsedPostDataCache.getParsedPostData(
          oldPostData.postDescriptor.threadDescriptor,
          oldPostData.postDescriptor
        )?.parsedPostDataContext

        val highlightedPostDescriptor = when (postViewMode) {
          is PopupPostsScreen.PostViewMode.PostList -> null
          is PopupPostsScreen.PostViewMode.RepliesFrom -> postViewMode.postDescriptor
          is PopupPostsScreen.PostViewMode.ReplyTo -> postViewMode.postDescriptor
        }

        val newParsedPostDataContext = oldParsedPostDataContext
          ?.copy(
            isParsingCatalog = postViewMode.isCatalogMode,
            highlightedPostDescriptor = highlightedPostDescriptor
          )
          ?: ParsedPostDataContext(
            isParsingCatalog = postViewMode.isCatalogMode,
            highlightedPostDescriptor = highlightedPostDescriptor
          )

        val parsedPostData = parsedPostDataCache.calculateParsedPostData(
          postData = oldPostData,
          parsedPostDataContext = newParsedPostDataContext,
          chanTheme = chanTheme
        )

        return@map PostCellData.fromPostData(
          postData = oldPostData,
          parsedPostData = parsedPostData
        )
      }
    }

    when (postViewMode) {
      is PopupPostsScreen.PostViewMode.RepliesFrom -> {
        parsedReplyFromCache.put(postViewMode.postDescriptor, updatedPostDataList)
      }
      is PopupPostsScreen.PostViewMode.ReplyTo -> {
        parsedReplyToCache.put(postViewMode.postDescriptor, updatedPostDataList)
      }
      is PopupPostsScreen.PostViewMode.PostList -> {
        updatedPostDataList.forEach { postCellData ->
          parsedPostsCache[postCellData.postDescriptor] = postCellData
        }
      }
    }

    return updatedPostDataList
  }

}