package com.github.k1rakishou.kurobaexlite.model.data.local.bookmark

import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

data class ThreadBookmarkSortOrder(
  val threadDescriptor: ThreadDescriptor,
  val sortOrder: Int
)