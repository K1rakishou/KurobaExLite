package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class PostData(
  val postDescriptor: PostDescriptor,
  val postSubjectUnparsed: String,
  val postCommentUnparsed: String,
  val images: List<PostImageData>?,
  val threadRepliesTotal: Int? = null,
  val threadImagesTotal: Int? = null,
  val threadPostersTotal: Int? = null,
  @Volatile private var _parsedPostData: ParsedPostData?
) {
  private val mutex = Mutex()

  val postNo: Long
    get() = postDescriptor.postNo
  val postSubNo: Long?
    get() = postDescriptor.postSubNo
  val isOP: Boolean
    get() = postNo == postDescriptor.threadNo
  val parsedPostData: ParsedPostData?
    get() = _parsedPostData

  val postCommentParsedAndProcessed: AnnotatedString?
    get() = _parsedPostData?.processedPostComment
  val postSubjectParsedAndProcessed: AnnotatedString?
    get() = _parsedPostData?.processedPostSubject
  val parsedPostDataContext: ParsedPostDataContext?
    get() = _parsedPostData?.parsedPostDataContext

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

  fun copy(
    postDescriptor: PostDescriptor = this.postDescriptor,
    postSubjectUnparsed: String = this.postSubjectUnparsed,
    postCommentUnparsed: String = this.postCommentUnparsed,
    images: List<PostImageData>? = this.images,
    parsedPostData: ParsedPostData? = this.parsedPostData,
  ): PostData {
    return PostData(
      postDescriptor = postDescriptor,
      postSubjectUnparsed = postSubjectUnparsed,
      postCommentUnparsed = postCommentUnparsed,
      images = images,
      _parsedPostData = parsedPostData
    )
  }

  suspend fun getOrCalculateParsedPostParts(
    force: Boolean,
    calcFunc: suspend () -> ParsedPostData
  ): ParsedPostData {
    return mutex.withLock {
      if (!force && _parsedPostData != null) {
        return@withLock _parsedPostData!!
      }

      _parsedPostData = calcFunc()
      return@withLock _parsedPostData!!
    }
  }

  fun formatToolbarTitle(catalogMode: Boolean): String? {
    if (catalogMode) {
      return "${postDescriptor.siteKeyActual}/${postDescriptor.boardCode}/"
    }

    val parsedPostData = _parsedPostData
      ?: return null

    if (parsedPostData.parsedPostSubject.isNotNullNorBlank()) {
      return parsedPostData.parsedPostSubject
    }

    return parsedPostData.parsedPostComment.take(64)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PostData

    if (postDescriptor != other.postDescriptor) return false
    if (postSubjectUnparsed != other.postSubjectUnparsed) return false
    if (postCommentUnparsed != other.postCommentUnparsed) return false
    if (images != other.images) return false
    if (_parsedPostData != other._parsedPostData) return false

    return true
  }

  override fun hashCode(): Int {
    var result = postDescriptor.hashCode()
    result = 31 * result + postSubjectUnparsed.hashCode()
    result = 31 * result + postCommentUnparsed.hashCode()
    result = 31 * result + (images?.hashCode() ?: 0)
    result = 31 * result + (_parsedPostData?.hashCode() ?: 0)
    return result
  }

}