package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.MurmurHashUtils
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

open class PostData(
  val postIndex: Int,
  val postDescriptor: PostDescriptor,
  val postSubjectUnparsed: String,
  val postCommentUnparsed: String,
  val timeMs: Long?,
  val images: List<PostImageData>?,
  val threadRepliesTotal: Int? = null,
  val threadImagesTotal: Int? = null,
  val threadPostersTotal: Int? = null,
  @Volatile private var parsedPostData: ParsedPostData?
) {
  @Volatile private var _murmur3HashMut = MurmurHashUtils.Murmur3Hash.EMPTY
  val murmur3Hash: MurmurHashUtils.Murmur3Hash
    get() = _murmur3HashMut

  val postNo: Long
    get() = postDescriptor.postNo
  val postSubNo: Long?
    get() = postDescriptor.postSubNo
  val isOP: Boolean
    get() = postNo == postDescriptor.threadNo

  val postCommentParsedAndProcessed: AnnotatedString?
    get() = parsedPostData?.processedPostComment
  val postSubjectParsedAndProcessed: AnnotatedString?
    get() = parsedPostData?.processedPostSubject
  val parsedPostDataContext: ParsedPostDataContext?
    get() = parsedPostData?.parsedPostDataContext
  val postFooterText: AnnotatedString?
    get() = parsedPostData?.postFooterText

  init {
    recalculateHash()
  }

  private fun recalculateHash() {
    _murmur3HashMut = MurmurHashUtils.murmurhash3_x64_128(postDescriptor)
      .combine(MurmurHashUtils.murmurhash3_x64_128(postSubjectUnparsed))
      .combine(MurmurHashUtils.murmurhash3_x64_128(postCommentUnparsed))
      .combine(MurmurHashUtils.murmurhash3_x64_128(timeMs))
      .combine(MurmurHashUtils.murmurhash3_x64_128(images))
      .combine(MurmurHashUtils.murmurhash3_x64_128(threadRepliesTotal))
      .combine(MurmurHashUtils.murmurhash3_x64_128(threadImagesTotal))
      .combine(MurmurHashUtils.murmurhash3_x64_128(threadPostersTotal))
      .combine((parsedPostData?.murmurhash()) ?: MurmurHashUtils.Murmur3Hash.EMPTY)
  }

  fun updateParsedPostData(newParsedPostData: ParsedPostData) {
    this.parsedPostData = newParsedPostData
    recalculateHash()
  }

  fun differsWith(other: PostData): Boolean {
    check(postDescriptor == other.postDescriptor) {
      "PostDescriptors differ: " +
        "this.postDescriptor=${postDescriptor}, " +
        "other.postDescriptor=${other.postDescriptor}"
    }

    if (threadRepliesTotal != other.threadRepliesTotal) {
      return true
    }
    if (threadImagesTotal != other.threadImagesTotal) {
      return true
    }
    if (threadPostersTotal != other.threadPostersTotal) {
      return true
    }
    if (imagesDiffer(other)) {
      return true
    }
    if (postCommentUnparsed != other.postCommentUnparsed) {
      return true
    }
    if (postSubjectUnparsed != other.postSubjectUnparsed) {
      return true
    }

    return false
  }

  private fun imagesDiffer(other: PostData): Boolean {
    val thisImagesCount = images?.size ?: 0
    val otherImagesCount = other.images?.size ?: 0

    if (thisImagesCount != otherImagesCount) {
      return true
    }

    for (index in 0 until thisImagesCount) {
      val thisImage = images?.getOrNull(index)
      val otherImage = other.images?.getOrNull(index)

      if (thisImage != otherImage) {
        return true
      }
    }

    return false
  }

  open fun copy(
    postIndex: Int = this.postIndex,
    postDescriptor: PostDescriptor = this.postDescriptor,
    postSubjectUnparsed: String = this.postSubjectUnparsed,
    postCommentUnparsed: String = this.postCommentUnparsed,
    timeMs: Long? = this.timeMs,
    images: List<PostImageData>? = this.images,
    threadRepliesTotal: Int? = this.threadRepliesTotal,
    threadImagesTotal: Int? = this.threadImagesTotal,
    threadPostersTotal: Int? = this.threadPostersTotal,
    parsedPostData: ParsedPostData? = this.parsedPostData
  ): PostData {
    return PostData(
      postIndex = postIndex,
      postDescriptor = postDescriptor,
      postSubjectUnparsed = postSubjectUnparsed,
      postCommentUnparsed = postCommentUnparsed,
      timeMs = timeMs,
      images = images,
      threadRepliesTotal = threadRepliesTotal,
      threadImagesTotal = threadImagesTotal,
      threadPostersTotal = threadPostersTotal,
      parsedPostData = parsedPostData
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PostData

    if (postDescriptor != other.postDescriptor) return false
    if (postSubjectUnparsed != other.postSubjectUnparsed) return false
    if (postCommentUnparsed != other.postCommentUnparsed) return false
    if (timeMs != other.timeMs) return false
    if (images != other.images) return false
    if (parsedPostData != other.parsedPostData) return false

    return true
  }

  override fun hashCode(): Int {
    var result = postDescriptor.hashCode()
    result = 31 * result + postSubjectUnparsed.hashCode()
    result = 31 * result + postCommentUnparsed.hashCode()
    result = 31 * result + timeMs.hashCode()
    result = 31 * result + (images?.hashCode() ?: 0)
    result = 31 * result + (parsedPostData?.hashCode() ?: 0)
    return result
  }

}