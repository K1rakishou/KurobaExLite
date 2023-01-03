package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

data class ChanPostHide(
  val postDescriptor: PostDescriptor,
  val applyToReplies: Boolean,
  val manuallyUnhidden: Boolean,
  val reason: String
) {

  fun hideFlagsDiffer(other: ChanPostHide): Boolean {
    if (applyToReplies != other.applyToReplies) {
      return true
    }

    if (manuallyUnhidden != other.manuallyUnhidden) {
      return true
    }

    return false
  }

  fun toPostHideUi(): PostCellData.PostHideUi {
    return PostCellData.PostHideUi(reason)
  }

}