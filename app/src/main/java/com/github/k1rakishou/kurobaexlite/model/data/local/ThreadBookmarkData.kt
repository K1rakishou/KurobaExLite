package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import okhttp3.HttpUrl

class ThreadBookmarkData(
  val threadDescriptor: ThreadDescriptor,
  val postObjects: List<ThreadBookmarkInfoPostObject>,
  val bookmarkThumbnailUrl: HttpUrl?
) {

  fun getPostsCountWithoutOP(): Int {
    check(postObjects.first() is ThreadBookmarkInfoPostObject.OriginalPost) {
      "First post of ThreadBookmarkInfoObject is not OP"
    }

    return postObjects.size - 1
  }

  fun countAmountOfSeenPosts(lastViewedPostDescriptor: PostDescriptor?): Int {
    if (lastViewedPostDescriptor == null) {
      return postObjects.size
    }

    check(postObjects.first() is ThreadBookmarkInfoPostObject.OriginalPost) {
      "First post of ThreadBookmarkInfoObject is not OP"
    }

    return postObjects.count { threadBookmarkInfoPostObject ->
      threadBookmarkInfoPostObject.postDescriptor() <= lastViewedPostDescriptor
    }
  }

  fun lastThreadPostDescriptor(): PostDescriptor? {
    return postObjects.maxOfOrNull { simplePostObject -> simplePostObject.postDescriptor() }
  }

  fun subject(): String? {
    val originalPost = postObjects.firstOrNull() as? ThreadBookmarkInfoPostObject.OriginalPost
      ?: return null

    return originalPost.subject
  }

  fun originalPostComment(): String? {
    val originalPost = postObjects.firstOrNull() as? ThreadBookmarkInfoPostObject.OriginalPost
      ?: return null

    return originalPost.comment
  }

}

sealed class ThreadBookmarkInfoPostObject {

  fun comment(): String {
    return when (this) {
      is OriginalPost -> comment
      is RegularPost -> comment
    }
  }

  fun postDescriptor(): PostDescriptor {
    return when (this) {
      is OriginalPost -> postDescriptor
      is RegularPost -> postDescriptor
    }
  }

  data class OriginalPost(
    val postDescriptor: PostDescriptor,
    val closed: Boolean,
    val archived: Boolean,
    val isBumpLimit: Boolean,
    val isImageLimit: Boolean,
    val stickyThread: StickyThread,
    val thumbnailParams: ThumbnailParams?,
    val subject: String?,
    val comment: String
  ) : ThreadBookmarkInfoPostObject() {

    override fun toString(): String {
      return "OriginalPost(postDescriptor=$postDescriptor, closed=$closed, archived=$archived, " +
        "isBumpLimit=$isBumpLimit, isImageLimit=$isImageLimit, stickyThread=$stickyThread, " +
        "thumbnailParams=$thumbnailParams, subject=$subject, comment=$comment)"
    }
  }

  sealed interface ThumbnailParams {
    data class Chan4(val tim: Long?) : ThumbnailParams
    data class Dvach(val thumbnail: String?) : ThumbnailParams
  }

  data class RegularPost(
    val postDescriptor: PostDescriptor,
    val comment: String
  ) : ThreadBookmarkInfoPostObject() {

    override fun toString(): String {
      return "RegularPost(postDescriptor=$postDescriptor)"
    }
  }
}

sealed class StickyThread {
  object NotSticky : StickyThread()

  // Sticky thread without post cap.
  object StickyUnlimited : StickyThread()

  // Rolling sticky thread
  object StickyWithCap : StickyThread()

  override fun toString(): String {
    return when (this) {
      NotSticky -> "NotSticky"
      StickyUnlimited -> "StickyUnlimited"
      is StickyWithCap -> "StickyWithCap"
    }
  }

  companion object {
    fun create(isSticky: Boolean, stickyCap: Int): StickyThread {
      if (!isSticky) {
        return NotSticky
      }

      if (isSticky && stickyCap <= 0) {
        return StickyUnlimited
      }

      if (isSticky && stickyCap > 0) {
        return StickyWithCap
      }

      throw IllegalStateException("Bad StickyThread, isSticky: $isSticky, stickyCap: $stickyCap")
    }
  }
}