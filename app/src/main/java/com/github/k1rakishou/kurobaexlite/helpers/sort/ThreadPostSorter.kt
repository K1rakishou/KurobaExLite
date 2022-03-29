package com.github.k1rakishou.kurobaexlite.helpers.sort

import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThreadPostSorter : PostSorter() {

  suspend fun sortThreadPostCellData(
    threadPosts: Collection<PostCellData>,
  ): List<PostCellData> {
    return withContext(Dispatchers.Default) {
      sortPostCellData(
        ascending = true,
        posts = threadPosts
      ) { postDataState -> postDataState.originalPostOrder }
    }
  }

}