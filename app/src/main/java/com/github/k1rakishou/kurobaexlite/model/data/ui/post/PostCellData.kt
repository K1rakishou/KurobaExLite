package com.github.k1rakishou.kurobaexlite.model.data.ui.post

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.helpers.hash.MurmurHashUtils
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
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
  val images: List<PostCellImageData>?,
  val threadRepliesTotal: Int? = null,
  val threadImagesTotal: Int? = null,
  val threadPostersTotal: Int? = null,
  val lastModified: Long? = null,
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
        images = postData.images?.map { PostCellImageData.fromPostImageData(it) },
        threadRepliesTotal = postData.threadRepliesTotal,
        threadImagesTotal = postData.threadImagesTotal,
        threadPostersTotal = postData.threadPostersTotal,
        lastModified = postData.lastModified,
        parsedPostData = parsedPostData,
        postServerDataHashForListAnimations = postServerDataHashForListAnimations
      )
    }
  }

}