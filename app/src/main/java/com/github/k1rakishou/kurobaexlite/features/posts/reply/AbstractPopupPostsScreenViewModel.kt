package com.github.k1rakishou.kurobaexlite.features.posts.reply

import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PopupPostsScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.sort.CatalogThreadSorter
import com.github.k1rakishou.kurobaexlite.helpers.sort.ThreadPostSorter
import com.github.k1rakishou.kurobaexlite.helpers.util.flatMapNotNull
import com.github.k1rakishou.kurobaexlite.helpers.util.parallelForEachOrdered
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPosition
import com.github.k1rakishou.kurobaexlite.model.data.ui.LazyColumnRememberedPositionEvent
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultPopupPostsScreenViewModel(
  savedStateHandle: SavedStateHandle
) : AbstractPopupPostsScreenViewModel(savedStateHandle)

class MediaViewerPopupPostsScreenViewModel(
  savedStateHandle: SavedStateHandle
) : AbstractPopupPostsScreenViewModel(savedStateHandle)

abstract class AbstractPopupPostsScreenViewModel(savedStateHandle: SavedStateHandle) : PostScreenViewModel(savedStateHandle) {
  private val threadScreenState = PopupPostsScreenState()
  private val postDataStateMap = mutableMapOf<ScreenKey, PostDataState>()

  private val _scrollRestorationEventFlow = MutableSharedFlow<LazyColumnRememberedPositionEvent>(extraBufferCapacity = Channel.UNLIMITED)
  override val scrollRestorationEventFlow: SharedFlow<LazyColumnRememberedPositionEvent>
    get() = _scrollRestorationEventFlow.asSharedFlow()

  override val postScreenState: PostScreenState = threadScreenState

  override fun reload(loadOptions: LoadOptions, onReloadFinished: (() -> Unit)?) {
    error("Reloading reply popups is not supported")
  }

  override fun refresh() {
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
      is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> {
        postDataState.parsedReplyFromCache[postViewMode.postDescriptor] ?: emptyList()
      }
      is PopupPostsScreen.PopupPostViewMode.ReplyTo -> {
        postDataState.parsedReplyToCache[postViewMode.postDescriptor] ?: emptyList()
      }
      is PopupPostsScreen.PopupPostViewMode.PostList -> {
        postViewMode.asPostDescriptorList
          .mapNotNull { postDescriptor -> postDataState.parsedPostsCache[postDescriptor] }
      }
    }

    return cachedPostCellDataList.flatMapNotNull { postCellData -> postCellData.images }
  }

  suspend fun loadRepliesForModeInitial(
    screenKey: ScreenKey,
    popupPostViewMode: PopupPostsScreen.PopupPostViewMode,
  ) {
    _postsFullyParsedOnceFlow.value = false

    val chanDescriptor = popupPostViewMode.chanDescriptor
    val postDataState = getOrCreatePostDataStateForScreen(screenKey, chanDescriptor)

    if (postDataState.postReplyChainStack.isEmpty()) {
      loadRepliesForMode(
        screenKey = screenKey,
        popupPostViewMode = popupPostViewMode,
        isPushing = true
      )
    } else {
      val topReplyMode = postDataState.postReplyChainStack.last()

      loadRepliesForMode(
        screenKey = screenKey,
        popupPostViewMode = topReplyMode,
        isPushing = true
      )
    }

    _postsFullyParsedOnceFlow.value = true
  }

  suspend fun loadRepliesForMode(
    screenKey: ScreenKey,
    popupPostViewMode: PopupPostsScreen.PopupPostViewMode,
    rememberedPosition: LazyColumnRememberedPosition? = null,
    isPushing: Boolean = true
  ): Boolean {
    val chanDescriptor = popupPostViewMode.chanDescriptor
    val postDataState = getOrCreatePostDataStateForScreen(screenKey, chanDescriptor)

    val currentPostDescriptor = postDataState.postReplyChainStack.lastOrNull()?.let { popupPostViewMode ->
      when (popupPostViewMode) {
        is PopupPostsScreen.PopupPostViewMode.PostList -> null
        is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> popupPostViewMode.postDescriptor
        is PopupPostsScreen.PopupPostViewMode.ReplyTo -> popupPostViewMode.postDescriptor
      }
    }

    if (isPushing) {
      val indexOfExisting = postDataState.postReplyChainStack.indexOfFirst { it == popupPostViewMode }
      if (indexOfExisting >= 0) {
        // Move old on top of the stack
        postDataState.postReplyChainStack.add(postDataState.postReplyChainStack.removeAt(indexOfExisting))
      } else {
        // Add new on top of the stack
        postDataState.postReplyChainStack += popupPostViewMode
      }
    }

    postScreenState.postsAsyncDataState.value = AsyncData.Loading

    val result = when (popupPostViewMode) {
      is PopupPostsScreen.PopupPostViewMode.ReplyTo -> {
        loadReplyTo(screenKey, chanDescriptor, popupPostViewMode)
      }
      is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> {
        loadRepliesFrom(screenKey, chanDescriptor, popupPostViewMode)
      }
      is PopupPostsScreen.PopupPostViewMode.PostList -> {
        loadPostList(screenKey, chanDescriptor, popupPostViewMode)
      }
    }

    if (rememberedPosition != null && currentPostDescriptor != null) {
      postDataState.scrollPositions[currentPostDescriptor] = rememberedPosition
    }

    return result
  }

  suspend fun popReplyChain(screenKey: ScreenKey): Boolean {
    val postDataState = postDataStateMap.get(screenKey)
      ?: return false

    postDataState.postReplyChainStack.removeLastOrNull()
      ?: return false

    val prevMode = postDataState.postReplyChainStack.lastOrNull()
      ?: return false

    val rememberedPosition = when (prevMode) {
      is PopupPostsScreen.PopupPostViewMode.PostList -> null
      is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> {
        postDataState.scrollPositions.remove(prevMode.postDescriptor)
      }
      is PopupPostsScreen.PopupPostViewMode.ReplyTo -> {
        postDataState.scrollPositions.remove(prevMode.postDescriptor)
      }
    }

    if (rememberedPosition != null) {
      _scrollRestorationEventFlow.emit(rememberedPosition.toLazyColumnRememberedPositionEvent())
    }

    return loadRepliesForMode(
      screenKey = screenKey,
      popupPostViewMode = prevMode,
      rememberedPosition = rememberedPosition,
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
    postDataState.scrollPositions.clear()
    postDataState.parsedReplyToCache.evictAll()
    postDataState.parsedReplyFromCache.evictAll()
    postDataState.parsedPostsCache.clear()
  }

  private suspend fun loadPostList(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    popupPostViewMode: PopupPostsScreen.PopupPostViewMode.PostList
  ): Boolean {
    postScreenState.updateChanDescriptor(popupPostViewMode.chanDescriptor)

    val posts = parsePostDataList(
      screenKey = screenKey,
      chanDescriptor = chanDescriptor,
      popupPostViewMode = popupPostViewMode,
      postCellDataList = chanPostCache.getManyForDescriptor(
        chanDescriptor = popupPostViewMode.chanDescriptor,
        postDescriptors = popupPostViewMode.asPostDescriptorList
      )
    )

    val sortedPosts = when (popupPostViewMode.chanDescriptor) {
      is CatalogDescriptor -> CatalogThreadSorter.sortCatalogPostCellData(posts, appSettings.catalogSort.read())
      is ThreadDescriptor -> ThreadPostSorter.sortThreadPostCellData(posts)
    }

    val filteredPosts = postHideHelper.filterPosts(chanDescriptor, sortedPosts)

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(popupPostViewMode.chanDescriptor))
    postScreenState.insertOrUpdateMany(filteredPosts)

    return true
  }

  private suspend fun loadRepliesFrom(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    popupPostViewMode: PopupPostsScreen.PopupPostViewMode.RepliesFrom
  ): Boolean {
    val postDescriptor = popupPostViewMode.postDescriptor
    postScreenState.updateChanDescriptor(chanDescriptor)

    val repliesFrom = postReplyChainRepository.getRepliesFrom(postDescriptor).toMutableSet()
    if (popupPostViewMode.includeThisPost) {
      repliesFrom += postDescriptor
    }

    val posts = parsePostDataList(
      screenKey = screenKey,
      chanDescriptor = chanDescriptor,
      popupPostViewMode = popupPostViewMode,
      postCellDataList = chanPostCache.getManyForDescriptor(chanDescriptor, repliesFrom)
    )

    val sortedPosts = ThreadPostSorter.sortThreadPostCellData(posts)
    val filteredPosts = postHideHelper.filterPosts(chanDescriptor, sortedPosts)

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(chanDescriptor))
    postScreenState.insertOrUpdateMany(filteredPosts)

    return true
  }

  private suspend fun loadReplyTo(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    popupPostViewMode: PopupPostsScreen.PopupPostViewMode.ReplyTo
  ): Boolean {
    val postDescriptor = popupPostViewMode.postDescriptor
    postScreenState.updateChanDescriptor(chanDescriptor)

    val sortedPosts = when (chanDescriptor) {
      is CatalogDescriptor -> {
        val threadPosts = chanPostCache.getCatalogPost(postDescriptor)
          ?.let { postData ->
            return@let parsePostDataList(
              screenKey = screenKey,
              chanDescriptor = chanDescriptor,
              popupPostViewMode = popupPostViewMode,
              postCellDataList = listOf(postData)
            )
          }
          ?: emptyList()

        CatalogThreadSorter.sortCatalogPostCellData(threadPosts, appSettings.catalogSort.read())
      }
      is ThreadDescriptor -> {
        val threadPosts = chanPostCache.getThreadPost(postDescriptor)
          ?.let { postData ->
            return@let parsePostDataList(
              screenKey = screenKey,
              chanDescriptor = chanDescriptor,
              popupPostViewMode = popupPostViewMode,
              postCellDataList = listOf(postData)
            )
          }
          ?: emptyList()

        ThreadPostSorter.sortThreadPostCellData(threadPosts)
      }
    }

    val filteredPosts = postHideHelper.filterPosts(chanDescriptor, sortedPosts)

    postScreenState.postsAsyncDataState.value = AsyncData.Data(PostsState(chanDescriptor))
    postScreenState.insertOrUpdateMany(filteredPosts)

    return true
  }

  private suspend fun parsePostDataList(
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor,
    popupPostViewMode: PopupPostsScreen.PopupPostViewMode,
    postCellDataList: List<IPostData>
  ): List<PostCellData> {
    val postDataState = getOrCreatePostDataStateForScreen(screenKey, chanDescriptor)

    val fromCache = when (popupPostViewMode) {
      is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> {
        postDataState.parsedReplyFromCache[popupPostViewMode.postDescriptor]
      }
      is PopupPostsScreen.PopupPostViewMode.ReplyTo -> {
        postDataState.parsedReplyToCache[popupPostViewMode.postDescriptor]
      }
      is PopupPostsScreen.PopupPostViewMode.PostList -> {
        popupPostViewMode.asPostDescriptorList
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
    val postViewMode = PostViewMode.List

    val updatedPostDataList = parallelForEachOrdered(
      dataList = postCellDataList,
      parallelization = AppConstants.coresCount.coerceAtLeast(2),
      dispatcher = Dispatchers.Default
    ) { oldPostData ->
      val oldParsedPostDataContext = parsedPostDataRepository.getParsedPostData(
        chanDescriptor = chanDescriptor,
        postDescriptor = oldPostData.postDescriptor
      )?.parsedPostDataContext

      val highlightedPostDescriptor = when (popupPostViewMode) {
        is PopupPostsScreen.PopupPostViewMode.PostList -> null
        is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> popupPostViewMode.postDescriptor
        is PopupPostsScreen.PopupPostViewMode.ReplyTo -> popupPostViewMode.postDescriptor
      }

      val newParsedPostDataContext = oldParsedPostDataContext
        ?.copy(
          isParsingCatalog = chanDescriptor is CatalogDescriptor,
          postViewMode = postViewMode,
          highlightedPostDescriptor = highlightedPostDescriptor
        )
        ?: ParsedPostDataContext(
          isParsingCatalog = chanDescriptor is CatalogDescriptor,
          postViewMode = postViewMode,
          highlightedPostDescriptor = highlightedPostDescriptor
        )

      val parsedPostData = parsedPostDataRepository.calculateParsedPostData(
        postData = oldPostData,
        parsedPostDataContext = newParsedPostDataContext,
        chanTheme = chanTheme
      )

      val postHideUi = postHideRepository.postHideForPostDescriptor(oldPostData.postDescriptor)
        ?.toPostHideUi()

      return@parallelForEachOrdered PostCellData.fromPostData(
        chanDescriptor = chanDescriptor,
        postData = oldPostData,
        parsedPostData = parsedPostData,
        postHideUi = postHideUi
      )
    }

    when (popupPostViewMode) {
      is PopupPostsScreen.PopupPostViewMode.RepliesFrom -> {
        postDataState.parsedReplyFromCache.put(popupPostViewMode.postDescriptor, updatedPostDataList)
      }
      is PopupPostsScreen.PopupPostViewMode.ReplyTo -> {
        postDataState.parsedReplyToCache.put(popupPostViewMode.postDescriptor, updatedPostDataList)
      }
      is PopupPostsScreen.PopupPostViewMode.PostList -> {
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
    val postReplyChainStack = mutableListOf<PopupPostsScreen.PopupPostViewMode>()
    val scrollPositions = mutableMapOf<PostDescriptor, LazyColumnRememberedPosition>()
    val parsedReplyToCache = LruCache<PostDescriptor, List<PostCellData>>(32)
    val parsedReplyFromCache = LruCache<PostDescriptor, List<PostCellData>>(32)
    val parsedPostsCache = mutableMapOf<PostDescriptor, PostCellData>()
  }

}