package com.github.k1rakishou.kurobaexlite.helpers.sort

import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData

abstract class PostSorter {

  protected inline fun sortPostCellData(
    ascending: Boolean,
    posts: Collection<PostCellData>,
    crossinline selector: (PostCellData) -> Comparable<*>
  ): List<PostCellData> {
    val comparator = if (ascending) {
      compareBy<PostCellData>(selector)
    } else {
      compareByDescending<PostCellData>(selector)
    }

    return posts.sortedWith(comparator)
  }

  protected inline fun sortPostData(
    ascending: Boolean,
    posts: Collection<IPostData>,
    crossinline selector: (IPostData) -> Comparable<*>
  ): List<IPostData> {
    val comparator = if (ascending) {
      compareBy<IPostData>(selector)
    } else {
      compareByDescending<IPostData>(selector)
    }

    return posts.sortedWith(comparator)
  }

}