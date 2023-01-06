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

    val repliesToHiddenPosts = postReplyChainRepository.getRepliesTo(postDescriptor)
      .filter { postDescriptor -> postHideRepository.isPostHidden(postDescriptor) }

    chanPostHides += ChanPostHide(
      postDescriptor = postDescriptor,
      applyToReplies = applyToReplies,
      hiddenManually = true
    ).also { chanPostHide -> chanPostHide.addReplies(repliesToHiddenPosts) }

    if (applyToReplies) {
      chanPostHides += findRepliesToMapToChanPostHides(postDescriptor)
    }

    postHideRepository.createOrUpdate(chanDescriptor, chanPostHides)
  }

  suspend fun unhide(postDescriptor: PostDescriptor) {
    if (!postHideRepository.isPostHidden(postDescriptor)) {
      return
    }

    postHideRepository.update(postDescriptor) { chanPostHide -> chanPostHide.unhidePost() }
  }

  private suspend fun findRepliesToMapToChanPostHides(
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
      val repliesToHiddenPosts = postReplyChainRepository.getRepliesTo(postDescriptor)
        .filter { postDescriptor -> postHideRepository.isPostHidden(postDescriptor) }

      return@map ChanPostHide(
        postDescriptor = postDescriptor,
        applyToReplies = true,
        hiddenManually = true
      ).also { chanPostHide -> chanPostHide.addReplies(repliesToHiddenPosts) }
    }
  }

}