package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanPostHideEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanPostHideReplyEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.PostKey
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import org.joda.time.DateTime

class ChanPostHide(
  val postDescriptor: PostDescriptor,
  val applyToReplies: Boolean,
  val state: State = State.Unspecified,
  repliesToHiddenPosts: Set<PostDescriptor> = emptySet()
) {
  private val repliesToHiddenPosts = mutableSetOf<PostDescriptor>()
  val repliesToHiddenPostsUnsafe: Set<PostDescriptor>
    get() = repliesToHiddenPosts

  init {
    this.repliesToHiddenPosts.forEach { postDescriptor ->
      check(this.postDescriptor != postDescriptor) { "Attempting to add reply to itself (${this.postDescriptor}, ${postDescriptor})" }
    }

    this.repliesToHiddenPosts.addAll(repliesToHiddenPosts)
  }

  @Synchronized
  fun isRoot(): Boolean {
    return repliesToHiddenPosts.isEmpty()
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
  fun removeRepliesMatching(postDescriptors: Set<PostDescriptor>) {
    val toRemove = mutableSetOf<PostDescriptor>()

    repliesToHiddenPosts.forEach { postDescriptor ->
      if (postDescriptor in postDescriptors) {
        toRemove += postDescriptor
      }
    }

    if (toRemove.isNotEmpty()) {
      repliesToHiddenPosts.removeAll(toRemove)
    }
  }

  @Synchronized
  fun hideFlagsDiffer(other: ChanPostHide): Boolean {
    if (applyToReplies != other.applyToReplies) {
      return true
    }

    if (state != other.state) {
      return true
    }

    if (repliesToHiddenPosts != other.repliesToHiddenPosts) {
      return true
    }

    return false
  }

  fun unhidePost(): ChanPostHide {
    return if (repliesToHiddenPosts.isEmpty()) {
      copy(state = State.Unspecified)
    } else {
      copy(state = State.UnhiddenManually)
    }
  }

  @Synchronized
  fun isHidden(): Boolean {
    if (state == State.UnhiddenManually) {
      return false
    } else if (state == State.HiddenManually) {
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
      if (state == State.UnhiddenManually) {
        return@run null
      } else if (state == State.HiddenManually) {
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

  fun toChanPostHideEntity(): ChanPostHideEntity {
    val state = when (state) {
      State.Unspecified -> ChanPostHideEntity.Unspecified
      State.HiddenManually -> ChanPostHideEntity.HiddenManually
      State.UnhiddenManually -> ChanPostHideEntity.UnhiddenManually
    }

    return ChanPostHideEntity(
      postKey = PostKey.fromPostDescriptor(postDescriptor),
      applyToReplies = applyToReplies,
      state = state,
      insertedOn = DateTime.now().millis
    )
  }

  fun toChanPostHideReplyEntity(): List<ChanPostHideReplyEntity> {
    return repliesToHiddenPosts.map { replyPostDescriptor ->
      return@map ChanPostHideReplyEntity(
        ownerPostKey = PostKey.fromPostDescriptor(postDescriptor),
        repliesToHiddenPost = PostKey.fromPostDescriptor(replyPostDescriptor)
      )
    }
  }

  fun copy(
    postDescriptor: PostDescriptor? = null,
    applyToReplies: Boolean? = null,
    state: State? = null,
    repliesToHiddenPosts: Set<PostDescriptor>? = null
  ): ChanPostHide {
    return ChanPostHide(
      postDescriptor = postDescriptor ?: this.postDescriptor,
      applyToReplies = applyToReplies ?: this.applyToReplies,
      state = state ?: this.state,
      repliesToHiddenPosts = repliesToHiddenPosts ?: this.repliesToHiddenPosts
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChanPostHide

    if (postDescriptor != other.postDescriptor) return false
    if (applyToReplies != other.applyToReplies) return false
    if (state != other.state) return false
    if (repliesToHiddenPosts != other.repliesToHiddenPosts) return false

    return true
  }

  override fun hashCode(): Int {
    var result = postDescriptor.hashCode()
    result = 31 * result + applyToReplies.hashCode()
    result = 31 * result + state.hashCode()
    result = 31 * result + repliesToHiddenPosts.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanPostHide(postDescriptor=$postDescriptor, applyToReplies=$applyToReplies, state=${state}, repliesToHiddenPosts=$repliesToHiddenPosts)"
  }

  enum class State {
    Unspecified,
    HiddenManually,
    UnhiddenManually
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

    fun fromChanPostHideEntity(
      chanPostHideEntity: ChanPostHideEntity,
      chanPostHideReplyEntities: List<ChanPostHideReplyEntity>
    ): ChanPostHide {
      val state = when (chanPostHideEntity.state) {
        ChanPostHideEntity.Unspecified -> ChanPostHide.State.Unspecified
        ChanPostHideEntity.HiddenManually -> ChanPostHide.State.HiddenManually
        ChanPostHideEntity.UnhiddenManually -> ChanPostHide.State.UnhiddenManually
        else -> error("Unknown ChanPostHideEntity.stat: ${chanPostHideEntity.state}")
      }

      return ChanPostHide(
        postDescriptor = chanPostHideEntity.postKey.postDescriptor,
        applyToReplies = chanPostHideEntity.applyToReplies,
        state = state,
        repliesToHiddenPosts = chanPostHideReplyEntities.map { it.repliesToHiddenPost.postDescriptor }.toSet()
      )
    }

  }

}