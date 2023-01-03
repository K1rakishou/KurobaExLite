package com.github.k1rakishou.kurobaexlite.interactors.filtering

import com.github.k1rakishou.kurobaexlite.helpers.filtering.PostFilterHelper
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
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

    val reason = PostFilterHelper.hiddenManuallyReason(
      parsedPostDataRepository = parsedPostDataRepository,
      chanDescriptor = chanDescriptor,
      postDescriptor = postDescriptor
    )

    val chanPostHides = mutableListOf<ChanPostHide>()

    chanPostHides += ChanPostHide(
      postDescriptor = postDescriptor,
      applyToReplies = applyToReplies,
      manuallyUnhidden = false,
      reason = reason
    )

    if (applyToReplies) {
      chanPostHides += findRepliesToMapToChanPostHides(
        chanDescriptor = chanDescriptor,
        replyToPostDescriptor = postDescriptor
      )
    }

    postHideRepository.createOrUpdate(chanDescriptor, chanPostHides)
  }

  suspend fun unhide(postDescriptor: PostDescriptor) {
    if (!postHideRepository.isPostHidden(postDescriptor)) {
      return
    }

    postHideRepository.update(postDescriptor) { chanPostHide -> chanPostHide.copy(manuallyUnhidden = true) }
  }

  private suspend fun findRepliesToMapToChanPostHides(
    chanDescriptor: ChanDescriptor,
    replyToPostDescriptor: PostDescriptor
  ): List<ChanPostHide> {
    val resultPostDescriptors = mutableSetOf<PostDescriptor>()

    postReplyChainRepository.findPostWithRepliesRecursive(
      postDescriptor = replyToPostDescriptor,
      includeRepliesFrom = true,
      includeRepliesTo = false,
      resultPostDescriptors = resultPostDescriptors
    )

    return resultPostDescriptors.map { postDescriptor ->
      val reason = PostFilterHelper.replyToHiddenPostReason(
        processingCatalog = chanDescriptor is CatalogDescriptor,
        postDescriptor = postDescriptor,
        replyToPostDescriptor = replyToPostDescriptor
      )

      return@map ChanPostHide(
        postDescriptor = postDescriptor,
        applyToReplies = true,
        manuallyUnhidden = false,
        reason = reason
      )
    }
  }

}