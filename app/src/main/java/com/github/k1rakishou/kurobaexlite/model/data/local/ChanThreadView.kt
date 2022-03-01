package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class ChanThreadView(
  var lastViewedPostDescriptor: PostDescriptor? = null,
  var lastLoadedPostDescriptor: PostDescriptor? = null,
  var scrollToPost: PostDescriptor? = null
)