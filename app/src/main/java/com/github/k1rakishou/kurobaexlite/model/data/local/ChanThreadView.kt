package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

data class ChanThreadView(
  val threadDescriptor: ThreadDescriptor,
  var lastViewedPDForIndicator: PostDescriptor? = null,
  var lastViewedPostDescriptor: PostDescriptor? = null,
  var lastLoadedPostDescriptor: PostDescriptor? = null,
  var scrollToPost: PostDescriptor? = null
)