package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.ThreadCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.AbstractPostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class CatalogScreenState : PostScreenViewModel.PostScreenState() {
  override val postsAsyncDataState = MutableStateFlow<AsyncData<AbstractPostsState>>(AsyncData.Empty)
  override val threadCellDataState = MutableStateFlow<ThreadCellData?>(null)
  override val lastViewedPostDescriptor = MutableStateFlow<PostDescriptor?>(null)
  override val searchQueryFlow = MutableStateFlow<String?>(null)

  override fun updatePost(postData: PostData) {
    val asyncData = postsAsyncDataState.value
    if (asyncData is AsyncData.Data) {
      asyncData.data.update(postData)
    }
  }

  override fun updatePosts(postDataCollection: Collection<PostData>) {
    val asyncData = postsAsyncDataState.value
    if (asyncData is AsyncData.Data) {
      asyncData.data.updateMany(postDataCollection)
    }
  }

  override fun updateSearchQuery(searchQuery: String?) {
    val asyncData = postsAsyncDataState.value
    if (asyncData is AsyncData.Data) {
      searchQueryFlow.value = searchQuery
      asyncData.data.updateSearchQuery(searchQuery)
    } else {
      searchQueryFlow.value = null
    }
  }
}