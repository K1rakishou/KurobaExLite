package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class ChanPostHide(
  val postDescriptor: PostDescriptor,
  val applyToReplies: Boolean,
  val hiddenManually: Boolean,
  repliesToHiddenPosts: Set<PostDescriptor> = emptySet()
) {
  private val repliesToHiddenPosts = mutableSetOf<PostDescriptor>()

  init {
    this.repliesToHiddenPosts.forEach { postDescriptor ->
      check(this.postDescriptor != postDescriptor) { "Attempting to add reply to itself (${this.postDescriptor}, ${postDescriptor})" }
    }

    this.repliesToHiddenPosts.addAll(repliesToHiddenPosts)
  }

  @Synchronized
  fun repliesToHiddenPostsCount(): Int {
    return repliesToHiddenPosts.size
  }

  @Synchronized
  fun repliesToHiddenPostsContain(postDescriptor: PostDescriptor): Boolean {
    return repliesToHiddenPosts.contains(postDescriptor)
  }

  @Synchronized
  fun repliesToHiddenPostsAreEmpty(): Boolean {
    return repliesToHiddenPosts.isEmpty()
  }

  @Synchronized
  fun addReplies(postDescriptors: Collection<PostDescriptor>) {
    postDescriptors.forEach { postDescriptor ->
      check(this.postDescriptor != postDescriptor) { "Attempting to add reply to itself (${this.postDescriptor}, ${postDescriptor})" }
      repliesToHiddenPosts += postDescriptor
    }
  }

  @Synchronized
  fun removeReplies(postDescriptors: Collection<PostDescriptor>) {
    postDescriptors.forEach { postDescriptor ->
      check(this.postDescriptor != postDescriptor) { "Attempting to remove itself as reply (${this.postDescriptor}, ${postDescriptor})" }
      repliesToHiddenPosts -= postDescriptor
    }
  }

  @Synchronized
  fun clearPostHides() {
    repliesToHiddenPosts.clear()
  }

  @Synchronized
  fun hideFlagsDiffer(other: ChanPostHide): Boolean {
    if (applyToReplies != other.applyToReplies) {
      return true
    }

    if (hiddenManually != other.hiddenManually) {
      return true
    }

    if (repliesToHiddenPosts != other.repliesToHiddenPosts) {
      return true
    }

    return false
  }

  fun unhidePost(): ChanPostHide {
    val copiedChanPostHide = copy(hiddenManually = false)
    copiedChanPostHide.clearPostHides()
    return copiedChanPostHide
  }

  @Synchronized
  fun isHidden(): Boolean {
    if (hiddenManually) {
      return true
    }

    if (repliesToHiddenPosts.isNotEmpty()) {
      return true
    }

    return false
  }

  @Synchronized
  fun toPostHideUi(): PostCellData.PostHideUi? {
    val reason = kotlin.run {
      if (hiddenManually) {
        return@run formatPostHiddenManually(postDescriptor)
      }

      if (repliesToHiddenPosts.isNotEmpty()) {
        return@run formatPostHiddenForReplyingToHiddenPosts(postDescriptor, repliesToHiddenPosts)
      }

      return@run null
    }

    if (reason == null) {
      return null
    }

    return PostCellData.PostHideUi(reason)
  }

  fun copy(
    postDescriptor: PostDescriptor? = null,
    applyToReplies: Boolean? = null,
    hiddenManually: Boolean? = null,
    repliesToHiddenPosts: Set<PostDescriptor>? = null
  ): ChanPostHide {
    return ChanPostHide(
      postDescriptor = postDescriptor ?: this.postDescriptor,
      applyToReplies = applyToReplies ?: this.applyToReplies,
      hiddenManually = hiddenManually ?: this.hiddenManually,
      repliesToHiddenPosts = repliesToHiddenPosts ?: this.repliesToHiddenPosts
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChanPostHide

    if (postDescriptor != other.postDescriptor) return false
    if (applyToReplies != other.applyToReplies) return false
    if (hiddenManually != other.hiddenManually) return false
    if (repliesToHiddenPosts != other.repliesToHiddenPosts) return false

    return true
  }

  override fun hashCode(): Int {
    var result = postDescriptor.hashCode()
    result = 31 * result + applyToReplies.hashCode()
    result = 31 * result + hiddenManually.hashCode()
    result = 31 * result + repliesToHiddenPosts.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanPostHide(postDescriptor=$postDescriptor, applyToReplies=$applyToReplies, hiddenManually=$hiddenManually, repliesToHiddenPosts=$repliesToHiddenPosts)"
  }

  companion object {
    fun formatPostHiddenManually(postDescriptor: PostDescriptor): String {
      return "Post (${postDescriptor.postNo}) hidden manually"
    }

    fun formatPostHiddenForReplyingToHiddenPosts(
      postDescriptor: PostDescriptor,
      repliesToHiddenPosts: Set<PostDescriptor>
    ): String {
      return "Post (${postDescriptor.postNo}) hidden because it replies to ${repliesToHiddenPosts.size} hidden post(s)"
    }
  }

}