package com.github.k1rakishou.kurobaexlite.features.posts.shared.state

import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class ThreadScreenPostsState : PostScreenState(checkFirstPostIsOriginal = true) {
  val originalPostState = MutableStateFlow<PostCellData?>(null)

  override suspend fun insertOrUpdateMany(postCellDataCollection: Collection<PostCellData>) {
    super.insertOrUpdateMany(postCellDataCollection)

    postCellDataCollection.forEach { postCellData ->
      if (postCellData.isOP) {
        originalPostState.value = postCellData
      }
    }
  }
}