package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

data class ChanThreadView(
  val threadDescriptor: ThreadDescriptor,
  // Used for the red line which marks which posts have not been viewed yet
  var lastViewedPDForIndicator: PostDescriptor? = null,
  // Used to restore scroll position
  var lastViewedPDForScroll: PostDescriptor? = null,
  // Use to determine how many posts were made since the last visit
  var lastViewedPDForNewPosts: PostDescriptor? = null,
  // Last post of a thread the last time we visited it
  var lastLoadedPostDescriptor: PostDescriptor? = null,
  var scrollToPost: PostDescriptor? = null
)