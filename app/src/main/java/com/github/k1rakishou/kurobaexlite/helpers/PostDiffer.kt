package com.github.k1rakishou.kurobaexlite.helpers

import com.github.k1rakishou.kurobaexlite.model.data.IPostData

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
    if (one.name != other.name) {
      return true
    }
    if (one.opMark != other.opMark) {
      return true
    }
    if (one.sage != other.sage) {
      return true
    }
    if (one.tripcode != other.tripcode) {
      return true
    }
    if (one.posterId != other.posterId) {
      return true
    }
    if (one.countryFlag != other.countryFlag) {
      return true
    }
    if (one.boardFlag != other.boardFlag) {
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
    if (one.archived != other.archived) {
      return true
    }
    if (one.closed != other.closed) {
      return true
    }
    if (one.sticky != other.sticky) {
      return true
    }
    if (one.deleted != other.deleted) {
      return true
    }
    if (one.bumpLimit != other.bumpLimit) {
      return true
    }
    if (one.imageLimit != other.imageLimit) {
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

}