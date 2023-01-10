package com.github.k1rakishou.kurobaexlite.interactors.filtering

import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository

class HideOrUnhidePost(
  private val postHideRepository: IPostHideRepository,
  private val postReplyChainRepository: IPostReplyChainRepository,
  private val parsedPostDataRepository: ParsedPostDataRepository
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

    postHideRepository.createOrUpdate(chanDescriptor, chanPostHides)
  }

  suspend fun unhide(postDescriptor: PostDescriptor) {
    if (!postHideRepository.isPostHidden(postDescriptor)) {
      return
    }

    postHideRepository.update(postDescriptor) { chanPostHide -> chanPostHide.unhidePost() }
  }

}