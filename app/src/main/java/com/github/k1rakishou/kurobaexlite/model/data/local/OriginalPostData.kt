package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

data class OriginalPostData(
  override val originalPostOrder: Int,
  override val postDescriptor: PostDescriptor,
  override val postSubjectUnparsed: String,
  override val postCommentUnparsed: String,
  override val timeMs: Long?,
  override val images: List<PostImageData>?,
  override val threadRepliesTotal: Int?,
  override val threadImagesTotal: Int?,
  override val threadPostersTotal: Int?,
  override val lastModified: Long?,
  override val archived: Boolean?,
  override val closed: Boolean?,
  override val sticky: Boolean?,
  override val bumpLimit: Boolean?,
  override val imageLimit: Boolean?,
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
    archived: Boolean?,
    closed: Boolean?,
    sticky: Boolean?,
    bumpLimit: Boolean?,
    imageLimit: Boolean?,
  ): IPostData {
    return OriginalPostData(
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
      archived = archived,
      closed = closed,
      sticky = sticky,
      bumpLimit = bumpLimit,
      imageLimit = imageLimit,
    )
  }
}