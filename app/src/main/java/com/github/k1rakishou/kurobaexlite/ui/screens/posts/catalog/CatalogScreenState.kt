package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel

class CatalogScreenState : PostScreenViewModel.PostScreenState() {

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