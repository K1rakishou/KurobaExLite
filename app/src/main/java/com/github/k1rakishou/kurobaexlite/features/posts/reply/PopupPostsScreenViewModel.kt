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
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PopupPostsScreenViewModel(savedStateHandle: SavedStateHandle) : PostScreenViewModel(savedStateHandle) {
  private val threadScreenState = PopupPostsScreenState()
  private val postDataStateMap = mutableMapOf<ScreenKey, PostDataState>()

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload(loadOptions: LoadOptions, onReloadFinished: (() -> Unit)?) {
    error("Reloading reply popups is not supported")
  }

  override fun refresh(onRefreshFinished: (() -> Unit)?) {
    error("Refreshing reply popups is not supported")
  }

  private fun getOrCreatePostDataStateForScreen(screenKey: ScreenKey, chanDescriptor: ChanDescriptor): PostDataState {
    return postDataStateMap.getOrPut(
      key = screenKey,
      defaultValue = { PostDataState(chanDescriptor) }
    )
  }

  fun collectCurrentImages(screenKey: ScreenKey, chanDescriptor: ChanDescriptor): List<PostCellImageData> {
    val postDataState = getOrCreatePostDataStateForScreen(screenKey, chanDescriptor)

    val postViewMode = postDataState.postReplyChainStack.lastOrNull()
      ?: return emptyList()

    val cachedPostCellDataList = when (postViewMode) {
      is PopupPostsScreen.PostViewMode.RepliesFrom -> {
        postDataState.parsedReplyFromCache[postViewMode.postDescriptor] ?: emptyList()
      }
      is PopupPostsScreen.PostViewMode.ReplyTo -> {
        postDataState.parsedReplyToCache[postViewMode.postDescriptor] ?: emptyList()
      }
      is PopupPostsScreen.PostViewMode.PostList -> {
        postViewMode.asPostDescriptorList
          .mapNotNull { postDescriptor -> postDataState.parsedPostsCache[postDescriptor] }
      }
    }

    return cachedPostCellDataList.flatMapNotNull { postCellData -> postCellData.images }
  }

  suspend fun loadRepliesForModeInitial(
    screenKey: ScreenKey,
    postViewMode: PopupPostsScreen.PostViewMode,
  ) {
    val chanDescriptor = postViewMode.chanDescriptor
    val postDataState = getOrCreatePostDataStateForScreen(screenKey, chanDescriptor)

    if (postDataState.postReplyChainStack.isEmpty()) {
      loadRepliesForMode(
        screenKey = screenKey,
        postViewMode = postViewMode,
        isPushing = true
      )
    } else {
      val topReplyMode = postDataState.postReplyChainStack.last()

      loadRepliesForMode(
        screenKey = screenKey,
        postViewMode = topReplyMode,
        isPushing = true
      )
    }
  }

  suspend fun loadRepliesForMode(
    screenKey: ScreenKey,
    postViewMode: PopupPostsScreen.PostViewMode,
    isPushing: Boolean = true
  ): Boolean {
    val chanDescriptor = postViewMode.chanDescriptor
    val postDataState = getOrCreatePostDataStateForScreen(screenKey, chanDescriptor)

    if (isPushing) {
      val indexOfExisting = postDataState.postReplyChainStack.indexOfFirst { it == postViewMode }
      if (indexOfExisting >= 0) {
        // Move old on top of the stack
        postDataState.postReplyChainStack.add(postDataState.postReplyChainStack.removeAt(indexOfExisting))
      } else {
        // Add new on top of the stack
        postDataState.postReplyChainStack += postViewMode
      }
    }

    return when (postViewMode) {
      is PopupPostsScreen.PostViewMode.ReplyTo -> {
        loadReplyTo(screenKey, chanDescriptor, postViewMode)
      }
      is PopupPostsScreen.PostViewMode.RepliesFrom -> {
        loadRepliesFrom(screenKey, chanDescriptor, postViewMode)
      }
      is PopupPostsScreen.PostViewMode.PostList -> {
        loadPostList(screenKey, chanDescriptor, postViewMode)
      }
    }
  }

  suspend fun popReplyChain(screenKey: ScreenKey): Boolean {
    val postDataState = postDataStateMap.get(screenKey)
      ?: return false

    postDataState.postReplyChainStack.removeLastOrNull()
      ?: return false

    val prevMode = postDataState.postReplyChainStack.lastOrNull()
      ?: return false

    return loadRepliesForMode(
      screenKey = screenKey,
      postViewMode = prevMode,
      isPushing = false
    )
  }

  fun clearPostReplyChainStack(screenKey: ScreenKey) {
    val postDataState = postDataStateMap.remove(screenKey)
    if (postDataStateMap.isEmpty()) {
      postScreenState.postsAsyncDataState.value = AsyncData.Uninitialized
    }

    if (postDataState == null) {
      return
    }

    postDataState.postReplyChainStack.clear()
    postDataState.parsedReplyToCache.evictAll()
    postDataState.parsedReplyFromCache.evictAll()
    postDataState.parsedPostsCache.clear()
  }

  private suspend fun loadPostList(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    postViewMode: PopupPostsScreen.PostViewMode.PostList
  ): Boolean {
    postScreenState.updateChanDescriptor(postViewMode.chanDescriptor)

    val posts = parsePostDataList(
      screenKey = screenKey,
      chanDescriptor = chanDescriptor,
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

  private suspend fun loadRepliesFrom(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    postViewMode: PopupPostsScreen.PostViewMode.RepliesFrom
  ): Boolean {
    val postDescriptor = postViewMode.postDescriptor
    postScreenState.updateChanDescriptor(chanDescriptor)

    val repliesFrom = postReplyChainManager.getRepliesFrom(postDescriptor).toMutableSet()
    if (postViewMode.includeThisPost) {
      repliesFrom += postDescriptor
    }

    val posts = parsePostDataList(
      screenKey = screenKey,
      chanDescriptor = chanDescriptor,
      postViewMode = postViewMode,
      postCellDataList = chanCache.getManyForDescriptor(chanDescriptor, repliesFrom)
    )

    val sortedPosts = ThreadPostSorter.sortThreadPostCellData(posts)

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(chanDescriptor))
    postScreenState.insertOrUpdateMany(sortedPosts)

    return true
  }

  private suspend fun loadReplyTo(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    postViewMode: PopupPostsScreen.PostViewMode.ReplyTo
  ): Boolean {
    val postDescriptor = postViewMode.postDescriptor
    postScreenState.updateChanDescriptor(chanDescriptor)

    val sortedPosts = when (chanDescriptor) {
      is CatalogDescriptor -> {
        val threadPosts = chanCache.getCatalogPost(postDescriptor)
          ?.let { postData ->
            return@let parsePostDataList(
              screenKey = screenKey,
              chanDescriptor = chanDescriptor,
              postViewMode = postViewMode,
              postCellDataList = listOf(postData)
            )
          }
          ?: emptyList()

        CatalogThreadSorter.sortCatalogPostCellData(threadPosts, appSettings.catalogSort.read())
      }
      is ThreadDescriptor -> {
        val threadPosts = chanCache.getThreadPost(postDescriptor)
          ?.let { postData ->
            return@let parsePostDataList(
              screenKey = screenKey,
              chanDescriptor = chanDescriptor,
              postViewMode = postViewMode,
              postCellDataList = listOf(postData)
            )
          }
          ?: emptyList()

        ThreadPostSorter.sortThreadPostCellData(threadPosts)
      }
    }

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(chanDescriptor))
    postScreenState.insertOrUpdateMany(sortedPosts)

    return true
  }

  private suspend fun parsePostDataList(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    postViewMode: PopupPostsScreen.PostViewMode,
    postCellDataList: List<IPostData>
  ): List<PostCellData> {
    val postDataState = getOrCreatePostDataStateForScreen(screenKey, chanDescriptor)

    val fromCache = when (postViewMode) {
      is PopupPostsScreen.PostViewMode.RepliesFrom -> {
        postDataState.parsedReplyFromCache[postViewMode.postDescriptor]
      }
      is PopupPostsScreen.PostViewMode.ReplyTo -> {
        postDataState.parsedReplyToCache[postViewMode.postDescriptor]
      }
      is PopupPostsScreen.PostViewMode.PostList -> {
        postViewMode.asPostDescriptorList
          .mapNotNull { postDescriptor -> postDataState.parsedPostsCache[postDescriptor] }
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
          chanDescriptor = chanDescriptor,
          postDescriptor = oldPostData.postDescriptor
        )?.parsedPostDataContext

        val highlightedPostDescriptor = when (postViewMode) {
          is PopupPostsScreen.PostViewMode.PostList -> null
          is PopupPostsScreen.PostViewMode.RepliesFrom -> postViewMode.postDescriptor
          is PopupPostsScreen.PostViewMode.ReplyTo -> postViewMode.postDescriptor
        }

        val newParsedPostDataContext = oldParsedPostDataContext
          ?.copy(
            isParsingCatalog = chanDescriptor is CatalogDescriptor,
            highlightedPostDescriptor = highlightedPostDescriptor
          )
          ?: ParsedPostDataContext(
            isParsingCatalog = chanDescriptor is CatalogDescriptor,
            highlightedPostDescriptor = highlightedPostDescriptor
          )

        val parsedPostData = parsedPostDataCache.calculateParsedPostData(
          postData = oldPostData,
          parsedPostDataContext = newParsedPostDataContext,
          chanTheme = chanTheme
        )

        return@map PostCellData.fromPostData(
          chanDescriptor = chanDescriptor,
          postData = oldPostData,
          parsedPostData = parsedPostData
        )
      }
    }

    when (postViewMode) {
      is PopupPostsScreen.PostViewMode.RepliesFrom -> {
        postDataState.parsedReplyFromCache.put(postViewMode.postDescriptor, updatedPostDataList)
      }
      is PopupPostsScreen.PostViewMode.ReplyTo -> {
        postDataState.parsedReplyToCache.put(postViewMode.postDescriptor, updatedPostDataList)
      }
      is PopupPostsScreen.PostViewMode.PostList -> {
        updatedPostDataList.forEach { postCellData ->
          postDataState.parsedPostsCache[postCellData.postDescriptor] = postCellData
        }
      }
    }

    return updatedPostDataList
  }

  class PostDataState(
    val chanDescriptor: ChanDescriptor
  ) {
    val postReplyChainStack = mutableListOf<PopupPostsScreen.PostViewMode>()
    val parsedReplyToCache = LruCache<PostDescriptor, List<PostCellData>>(32)
    val parsedReplyFromCache = LruCache<PostDescriptor, List<PostCellData>>(32)
    val parsedPostsCache = mutableMapOf<PostDescriptor, PostCellData>()
  }

}