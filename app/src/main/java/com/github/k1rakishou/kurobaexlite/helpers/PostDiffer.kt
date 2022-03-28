package com.github.k1rakishou.kurobaexlite.helpers

import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData

object PostDiffer {

  fun postsDiffer(one: IPostData, other: IPostData): Boolean {
    check(one.postDescriptor == other.postDescriptor) {
      "PostDescriptors differ: " +
        "this.postDescriptor=${one.postDescriptor}, " +
        "other.postDescriptor=${other.postDescriptor}"
    }

    if (one.threadRepliesTotal != other.threadRepliesTotal) {
      return true
    }
    if (one.timeMs != other.timeMs) {
      return true
    }
    if (one.lastModified != other.lastModified) {
      return true
    }
    if (one.threadImagesTotal != other.threadImagesTotal) {
      return true
    }
    if (one.threadPostersTotal != other.threadPostersTotal) {
      return true
    }
    if (imagesDiffer(one, other)) {
      return true
    }
    if (one.postCommentUnparsed != other.postCommentUnparsed) {
      return true
    }
    if (one.postSubjectUnparsed != other.postSubjectUnparsed) {
      return true
    }

    return false
  }

  private fun imagesDiffer(one: IPostData, other: IPostData): Boolean {
    val thisImagesCount = one.images?.size ?: 0
    val otherImagesCount = other.images?.size ?: 0

    if (thisImagesCount != otherImagesCount) {
      return true
    }

    for (index in 0 until thisImagesCount) {
      val thisImage = one.images?.getOrNull(index)
      val otherImage = other.images?.getOrNull(index)

      if (thisImage != otherImage) {
        return true
      }
    }

    return false
  }

  fun postsDiffer(one: PostCellData, other: PostCellData): Boolean {
    check(one.postDescriptor == other.postDescriptor) {
      "PostDescriptors differ: " +
        "this.postDescriptor=${one.postDescriptor}, " +
        "other.postDescriptor=${other.postDescriptor}"
    }

    if (one.threadRepliesTotal != other.threadRepliesTotal) {
      return true
    }
    if (one.timeMs != other.timeMs) {
      return true
    }
    if (one.lastModified != other.lastModified) {
      return true
    }
    if (one.threadImagesTotal != other.threadImagesTotal) {
      return true
    }
    if (one.threadPostersTotal != other.threadPostersTotal) {
      return true
    }
    if (imagesDiffer(one, other)) {
      return true
    }
    if (one.postCommentUnparsed != other.postCommentUnparsed) {
      return true
    }
    if (one.postSubjectUnparsed != other.postSubjectUnparsed) {
      return true
    }

    return false
  }

  private fun imagesDiffer(one: PostCellData, other: PostCellData): Boolean {
    val thisImagesCount = one.images?.size ?: 0
    val otherImagesCount = other.images?.size ?: 0

    if (thisImagesCount != otherImagesCount) {
      return true
    }

    for (index in 0 until thisImagesCount) {
      val thisImage = one.images?.getOrNull(index)
      val otherImage = other.images?.getOrNull(index)

      if (thisImage != otherImage) {
        return true
      }
    }

    return false
  }

}