package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state

import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import kotlinx.coroutines.flow.MutableStateFlow

class ThreadScreenPostsState : PostScreenState() {
  val originalPostState = MutableStateFlow<PostCellData?>(null)

  override fun insertOrUpdate(postCellData: PostCellData) {
    super.insertOrUpdate(postCellData)

    if (postCellData.isOP) {
      originalPostState.value = postCellData
    }
  }

  override fun insertOrUpdateMany(postCellDataCollection: Collection<PostCellData>) {
    super.insertOrUpdateMany(postCellDataCollection)

    postCellDataCollection.forEach { postCellData ->
      if (postCellData.isOP) {
        originalPostState.value = postCellData
      }
    }
  }
}