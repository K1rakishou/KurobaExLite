package com.github.k1rakishou.kurobaexlite.helpers.sort

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

}