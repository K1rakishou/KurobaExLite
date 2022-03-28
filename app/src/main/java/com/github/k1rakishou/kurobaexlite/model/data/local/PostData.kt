package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

data class PostData(
  override val originalPostOrder: Int,
  override val postDescriptor: PostDescriptor,
  override val postSubjectUnparsed: String,
  override val postCommentUnparsed: String,
  override val timeMs: Long?,
  override val images: List<PostImageData>?,
  override val threadRepliesTotal: Int? = null,
  override val threadImagesTotal: Int? = null,
  override val threadPostersTotal: Int? = null,
  override val lastModified: Long? = null
) : IPostData {

  override fun copy(
    originalPostOrder: Int,
    postDescriptor: PostDescriptor,
    postSubjectUnparsed: String,
    postCommentUnparsed: String,
    timeMs: Long?,
    images: List<IPostImage>?,
    threadRepliesTotal: Int?,
    threadImagesTotal: Int?,
    threadPostersTotal: Int?,
    lastModified: Long?,
  ): IPostData {
    return PostData(
      originalPostOrder = originalPostOrder,
      postDescriptor = postDescriptor,
      postSubjectUnparsed = postSubjectUnparsed,
      postCommentUnparsed = postCommentUnparsed,
      timeMs = timeMs,
      images = images as List<PostImageData>,
      threadRepliesTotal = threadRepliesTotal,
      threadImagesTotal = threadImagesTotal,
      threadPostersTotal = threadPostersTotal,
      lastModified = lastModified,
    )
  }

}