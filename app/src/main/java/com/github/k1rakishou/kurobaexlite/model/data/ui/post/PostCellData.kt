package com.github.k1rakishou.kurobaexlite.model.data.ui.post

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.helpers.hash.MurmurHashUtils
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

@Immutable
data class PostCellData(
  val originalPostOrder: Int,
  val postDescriptor: PostDescriptor,
  val postSubjectUnparsed: String,
  val postCommentUnparsed: String,
  val timeMs: Long?,
  val name: String?,
  val tripcode: String?,
  val posterId: String?,
  val countryFlag: PostIcon?,
  val boardFlag: PostIcon?,
  val images: List<PostCellImageData>?,
  val threadRepliesTotal: Int? = null,
  val threadImagesTotal: Int? = null,
  val threadPostersTotal: Int? = null,
  val lastModified: Long? = null,
  val archived: Boolean,
  val deleted: Boolean,
  val closed: Boolean,
  val sticky: Boolean,
  val bumpLimit: Boolean?,
  val imageLimit: Boolean?,
  val parsedPostData: ParsedPostData?,
  val postServerDataHashForListAnimations: Murmur3Hash?
) {

  val postNo: Long
    get() = postDescriptor.postNo
  val postSubNo: Long?
    get() = postDescriptor.postSubNo
  val isOP: Boolean
    get() = postNo == postDescriptor.threadNo
  val parsedPostDataContext: ParsedPostDataContext?
    get() = parsedPostData?.parsedPostDataContext

  fun hasSearchQuerySpans(): Boolean {
    if (parsedPostData == null) {
      return false
    }

    return hasSearchQuery(parsedPostData.processedPostComment) ||
      hasSearchQuery(parsedPostData.processedPostSubject)
  }

  private fun hasSearchQuery(string: AnnotatedString?): Boolean {
    if (string == null) {
      return false
    }

    return string.spanStyles.any { it.item.fontWeight != null }
  }

  companion object {
    fun fromPostData(
      postData: IPostData,
      parsedPostData: ParsedPostData?
    ): PostCellData {
      BackgroundUtils.ensureBackgroundThread()

      val postServerDataHashForListAnimations = if (parsedPostData != null) {
        MurmurHashUtils.murmurhash3_x64_128(postData.postDescriptor)
          .combine(MurmurHashUtils.murmurhash3_x64_128(postData.postSubjectUnparsed))
          .combine(MurmurHashUtils.murmurhash3_x64_128(postData.postCommentUnparsed))
          .combine(MurmurHashUtils.murmurhash3_x64_128(postData.timeMs))
          .combine(MurmurHashUtils.murmurhash3_x64_128(postData.images))
          .combine(MurmurHashUtils.murmurhash3_x64_128(parsedPostData.postFooterText))
      } else {
        null
      }

      return PostCellData(
        originalPostOrder = postData.originalPostOrder,
        postDescriptor = postData.postDescriptor,
        postSubjectUnparsed = postData.postSubjectUnparsed,
        postCommentUnparsed = postData.postCommentUnparsed,
        timeMs = postData.timeMs,
        name = postData.name,
        tripcode = postData.tripcode,
        posterId = postData.posterId,
        countryFlag = postData.countryFlag,
        boardFlag = postData.boardFlag,
        images = postData.images?.map { PostCellImageData.fromPostImageData(it) },
        threadRepliesTotal = postData.threadRepliesTotal,
        threadImagesTotal = postData.threadImagesTotal,
        threadPostersTotal = postData.threadPostersTotal,
        lastModified = postData.lastModified,
        archived = postData.archived,
        deleted = postData.deleted,
        closed = postData.closed,
        sticky = postData.sticky,
        bumpLimit = postData.bumpLimit,
        imageLimit = postData.imageLimit,
        parsedPostData = parsedPostData,
        postServerDataHashForListAnimations = postServerDataHashForListAnimations
      )
    }
  }

}