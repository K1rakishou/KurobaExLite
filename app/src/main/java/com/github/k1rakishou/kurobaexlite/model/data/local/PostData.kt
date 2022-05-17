package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

@Immutable
data class PostData(
  override val originalPostOrder: Int,
  override val postDescriptor: PostDescriptor,
  override val postSubjectUnparsed: String,
  override val postCommentUnparsed: String,
  override val timeMs: Long?,
  override val name: String?,
  override val tripcode: String?,
  override val posterId: String?,
  override val countryFlag: PostIcon?,
  override val boardFlag: PostIcon?,
  override val images: List<PostImageData>?,
  override val threadRepliesTotal: Int? = null,
  override val threadImagesTotal: Int? = null,
  override val threadPostersTotal: Int? = null,
  override val lastModified: Long? = null,
  override val archived: Boolean,
  override val deleted: Boolean,
  override val closed: Boolean,
  override val sticky: Boolean,
  override val bumpLimit: Boolean?,
  override val imageLimit: Boolean?,
) : IPostData {

  override fun copy(
    originalPostOrder: Int,
    postDescriptor: PostDescriptor,
    postSubjectUnparsed: String,
    postCommentUnparsed: String,
    timeMs: Long?,
    name: String?,
    tripcode: String?,
    posterId: String?,
    countryFlag: PostIcon?,
    boardFlag: PostIcon?,
    images: List<IPostImage>?,
    threadRepliesTotal: Int?,
    threadImagesTotal: Int?,
    threadPostersTotal: Int?,
    lastModified: Long?,
    archived: Boolean,
    deleted: Boolean,
    closed: Boolean,
    sticky: Boolean,
    bumpLimit: Boolean?,
    imageLimit: Boolean?,
  ): IPostData {
    return PostData(
      originalPostOrder = originalPostOrder,
      postDescriptor = postDescriptor,
      postSubjectUnparsed = postSubjectUnparsed,
      postCommentUnparsed = postCommentUnparsed,
      timeMs = timeMs,
      name = name,
      tripcode = tripcode,
      posterId = posterId,
      countryFlag = countryFlag,
      boardFlag = boardFlag,
      images = images as List<PostImageData>,
      threadRepliesTotal = threadRepliesTotal,
      threadImagesTotal = threadImagesTotal,
      threadPostersTotal = threadPostersTotal,
      lastModified = lastModified,
      archived = archived,
      deleted = deleted,
      closed = closed,
      sticky = sticky,
      bumpLimit = bumpLimit,
      imageLimit = imageLimit,
    )
  }

}