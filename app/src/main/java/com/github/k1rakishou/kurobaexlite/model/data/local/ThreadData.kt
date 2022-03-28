package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

data class ThreadData(
  val threadDescriptor: ThreadDescriptor,
  val threadPosts: List<IPostData>
)