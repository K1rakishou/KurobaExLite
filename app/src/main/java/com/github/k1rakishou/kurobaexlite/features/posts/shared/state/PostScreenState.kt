package com.github.k1rakishou.kurobaexlite.features.posts.shared.state

import androidx.annotation.CallSuper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadStatusCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Stable
abstract class PostScreenState(
  private val checkFirstPostIsOriginal: Boolean
) {
  val postsAsyncDataState = MutableStateFlow<AsyncData<PostsState>>(AsyncData.Uninitialized)
  val threadCellDataState = MutableStateFlow<ThreadStatusCellData?>(null)

  private val _searchQueryFlow = MutableStateFlow<String?>(null)
  val searchQueryFlow: StateFlow<String?>
    get() = _searchQueryFlow

  val currentSearchQuery: String?
    get() = searchQueryFlow.value

  val lastViewedPostDescriptorForScrollRestoration = MutableStateFlow<PostDescriptor?>(null)
  val lastViewedPostDescriptorForIndicator = MutableStateFlow<PostDescriptor?>(null)
  val lastLoadErrorState = MutableStateFlow<Throwable?>(null)

  private val _chanDescriptorFlow = MutableStateFlow<ChanDescriptor?>(null)
  val chanDescriptorFlow: StateFlow<ChanDescriptor?>
    get() = _chanDescriptorFlow.asStateFlow()
  val chanDescriptor: ChanDescriptor?
    get() = _chanDescriptorFlow.value

  private val _contentLoaded = MutableStateFlow(false)
  val contentLoaded: StateFlow<Boolean>
    get() = _contentLoaded.asStateFlow()

  fun getPosts(postDescriptors: Collection<PostDescriptor>): List<PostCellData> {
    val postAsyncData = postsAsyncDataState.value
    if (postAsyncData !is AsyncData.Data) {
      return emptyList()
    }

    return postAsyncData.data.getPosts(postDescriptors)
  }

  @CallSuper
  open fun insertOrUpdate(postCellData: PostCellData) {
    doWithDataState { postsState ->
      postsState.insertOrUpdate(
        postCellData = postCellData,
        checkFirstPostIsOriginal = checkFirstPostIsOriginal
      )
    }
  }

  @CallSuper
  open fun insertOrUpdateMany(postCellDataCollection: Collection<PostCellData>) {
    doWithDataState { postsState ->
      postsState.insertOrUpdateMany(
        postCellDataCollection = postCellDataCollection,
        checkFirstPostIsOriginal = checkFirstPostIsOriginal
      )
    }
  }

  fun onSearchQueryUpdated(searchQuery: String?) {
    doWithDataState { postsState ->
      Snapshot.withMutableSnapshot {
        postsState.onSearchQueryUpdated(searchQuery)
        _searchQueryFlow.value = searchQuery
      }
    }
  }

  fun updateChanDescriptor(chanDescriptor: ChanDescriptor?) {
    _chanDescriptorFlow.value = chanDescriptor
  }

  suspend fun onStartLoading(chanDescriptor: ChanDescriptor?) {
    _contentLoaded.value = false
  }

  suspend fun onEndLoading(chanDescriptor: ChanDescriptor?) {
    _contentLoaded.value = true
  }

  private fun <T> doWithDataState(func: (PostsState) -> T): T? {
    val postAsyncData = postsAsyncDataState.value
    if (postAsyncData is AsyncData.Data) {
      return func(postAsyncData.data)
    }

    return null
  }

}

@Stable
data class PreviousPostDataInfo(
  val hash: Murmur3Hash,
  val time: Long,
  val alreadyAnimatedInsertion: Boolean = false
)