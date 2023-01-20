package com.github.k1rakishou.kurobaexlite.interactors.filtering

import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository

class HideOrUnhidePost(
  private val postHideRepository: IPostHideRepository
) {

  suspend fun hide(chanDescriptor: ChanDescriptor, postDescriptor: PostDescriptor, applyToReplies: Boolean) {
    if (postHideRepository.isPostHidden(postDescriptor)) {
      return
    }

    val chanPostHides = mutableListOf<ChanPostHide>()

    chanPostHides += ChanPostHide(
      postDescriptor = postDescriptor,
      applyToReplies = applyToReplies,
      state = ChanPostHide.State.HiddenManually
    )

    postHideRepository.createOrUpdate(chanDescriptor, chanPostHides).unwrap()
  }

  suspend fun unhide(postDescriptor: PostDescriptor) {
    if (!postHideRepository.isPostHidden(postDescriptor)) {
      return
    }

    postHideRepository.update(postDescriptor) { chanPostHide -> chanPostHide.unhidePost() }.unwrap()
  }

}