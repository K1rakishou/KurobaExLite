package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state

import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PostScreenState {
  val postsAsyncDataState = MutableStateFlow<AsyncData<PostsState>>(AsyncData.Empty)
  val threadCellDataState = MutableStateFlow<ThreadCellData?>(null)
  val searchQueryFlow = MutableStateFlow<String?>(null)

  val lastViewedPostDescriptorForScrollRestoration = MutableStateFlow<PostDescriptor?>(null)
  val lastViewedPostDescriptorForIndicator = MutableStateFlow<PostDescriptor?>(null)

  private val _chanDescriptorFlow = MutableStateFlow<ChanDescriptor?>(null)
  val chanDescriptorFlow: StateFlow<ChanDescriptor?>
    get() = _chanDescriptorFlow.asStateFlow()
  val chanDescriptor: ChanDescriptor?
    get() = _chanDescriptorFlow.value

  val displayingPostsCount: Int?
    get() = doWithDataState { abstractPostsState -> abstractPostsState.posts.size }

  private val _contentLoaded = MutableStateFlow(false)
  val contentLoaded: StateFlow<Boolean>
    get() = _contentLoaded.asStateFlow()

  fun insertOrUpdate(postCellData: PostCellData) {
    val asyncData = postsAsyncDataState.value
    if (asyncData is AsyncData.Data) {
      asyncData.data.insertOrUpdate(postCellData)
    }
  }

  fun insertOrUpdateMany(postCellDataCollection: Collection<PostCellData>) {
    val asyncData = postsAsyncDataState.value
    if (asyncData is AsyncData.Data) {
      asyncData.data.insertOrUpdateMany(postCellDataCollection)
    }
  }

  fun updateChanDescriptor(chanDescriptor: ChanDescriptor?) {
    _chanDescriptorFlow.value = chanDescriptor
  }

  fun onStartLoading() {
    _contentLoaded.value = false
  }

  fun onEndLoading() {
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