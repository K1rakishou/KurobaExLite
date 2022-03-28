package com.github.k1rakishou.kurobaexlite.model.data

import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

@Stable
interface IPostData {
  val originalPostOrder: Int
  val postDescriptor: PostDescriptor
  val postSubjectUnparsed: String
  val postCommentUnparsed: String
  val timeMs: Long?
  val images: List<IPostImage>?
  val threadRepliesTotal: Int?
  val threadImagesTotal: Int?
  val threadPostersTotal: Int?
  val lastModified: Long?

  val postNo: Long
    get() = postDescriptor.postNo
  val postSubNo: Long
    get() = postDescriptor.postSubNo
  val isOP: Boolean
    get() = postDescriptor.isOP

  fun copy(
    originalPostOrder: Int = this.originalPostOrder,
    postDescriptor: PostDescriptor = this.postDescriptor,
    postSubjectUnparsed: String = this.postSubjectUnparsed,
    postCommentUnparsed: String = this.postCommentUnparsed,
    timeMs: Long? = this.timeMs,
    images: List<IPostImage>? = this.images,
    threadRepliesTotal: Int? = this.threadRepliesTotal,
    threadImagesTotal: Int? = this.threadImagesTotal,
    threadPostersTotal: Int? = this.threadPostersTotal,
    lastModified: Long? = this.lastModified,
  ): IPostData
}