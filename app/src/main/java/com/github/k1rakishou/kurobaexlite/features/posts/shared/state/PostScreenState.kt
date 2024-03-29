package com.github.k1rakishou.kurobaexlite.features.posts.shared.state

import androidx.annotation.CallSuper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.helpers.util.withMutableSnapshotRepeatable
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadStatusCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

@Stable
abstract class PostScreenState(
  private val checkFirstPostIsOriginal: Boolean
) {
  val postsAsyncDataState = MutableStateFlow<AsyncData<PostsState>>(AsyncData.Uninitialized)
  val threadCellDataState = MutableStateFlow<ThreadStatusCellData?>(null)

  private val _searchQueryFlow = mutableStateOf<String?>(null)
  val searchQueryFlow: State<String?>
    get() = _searchQueryFlow

  val currentSearchQuery: String?
    get() = searchQueryFlow.value

  val lastViewedPostForScrollRestoration = MutableStateFlow<LastViewedPostForScrollRestoration?>(null)
  val lastViewedPostForIndicator = MutableStateFlow<PostDescriptor?>(null)
  val lastLoadErrorState = MutableStateFlow<Throwable?>(null)

  private val _chanDescriptorFlow = MutableStateFlow<ChanDescriptor?>(null)
  val chanDescriptorFlow: StateFlow<ChanDescriptor?>
    get() = _chanDescriptorFlow.asStateFlow()
  val chanDescriptor: ChanDescriptor?
    get() = _chanDescriptorFlow.value

  private val _parsingInProgress = AtomicBoolean(false)
  val parsingInProgress: Boolean
    get() = _parsingInProgress.get()

  private val _contentLoaded = MutableStateFlow(false)
  val contentLoaded: StateFlow<Boolean>
    get() = _contentLoaded.asStateFlow()

  private val _contentDrawnOnce = MutableStateFlow(false)
  val contentDrawnOnce: StateFlow<Boolean>
    get() = _contentDrawnOnce.asStateFlow()

  val contentLoadedAndDrawnOnce: Boolean
    get() = contentLoaded.value && contentDrawnOnce.value

  fun getPosts(postDescriptors: Collection<PostDescriptor>): List<PostCellData> {
    val postAsyncData = postsAsyncDataState.value
    if (postAsyncData !is AsyncData.Data) {
      return emptyList()
    }

    return postAsyncData.data.getPosts(postDescriptors)
  }

  fun getPost(postDescriptor: PostDescriptor): PostCellData? {
    val postAsyncData = postsAsyncDataState.value
    if (postAsyncData !is AsyncData.Data) {
      return null
    }

    return postAsyncData.data.getPost(postDescriptor)
  }

  @CallSuper
  open suspend fun insertOrUpdateMany(postCellDataCollection: Collection<PostCellData>) {
    doWithDataState { postsState ->
      withMutableSnapshotRepeatable {
        postsState.insertOrUpdateMany(
          postCellDataCollection = postCellDataCollection,
          checkFirstPostIsOriginal = checkFirstPostIsOriginal
        )
      }
    }
  }

  suspend fun onSearchQueryUpdated(searchQuery: String?) {
    doWithDataState { postsState ->
      withMutableSnapshotRepeatable {
        if (_searchQueryFlow.value != searchQuery) {
          _searchQueryFlow.value = searchQuery
          postsState.onSearchQueryUpdated(searchQuery)
        }
      }
    }
  }

  fun updateChanDescriptor(chanDescriptor: ChanDescriptor?) {
    _chanDescriptorFlow.value = chanDescriptor
  }

  fun onStartLoading(chanDescriptor: ChanDescriptor?) {
    _contentLoaded.value = false
    _contentDrawnOnce.value = false
    _parsingInProgress.set(true)
  }

  fun onEndLoading(chanDescriptor: ChanDescriptor?) {
    _contentLoaded.value = postsAsyncDataState.value is AsyncData.Data
    _parsingInProgress.set(false)
  }

  fun onPostListDisposed(chanDescriptor: ChanDescriptor?) {
    _contentDrawnOnce.value = false
  }

  fun onContentDrawnOnce(chanDescriptor: ChanDescriptor?) {
    _contentDrawnOnce.value = true
  }

  private suspend fun <T> doWithDataState(func: suspend (PostsState) -> T): T? {
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
  val hidden: Boolean = false,
  val alreadyAnimatedInsertion: Boolean = false
)

data class LastViewedPostForScrollRestoration(
  val postDescriptor: PostDescriptor,
  val blink: Boolean
)