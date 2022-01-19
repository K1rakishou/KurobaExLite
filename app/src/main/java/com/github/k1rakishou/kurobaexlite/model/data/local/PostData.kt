package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

data class PostData(
  val postDescriptor: PostDescriptor,
  val postCommentUnparsed: String,
  val images: List<PostImageData>?
) {
  val postNo: Long
    get() = postDescriptor.postNo
  val postSubNo: Long?
    get() = postDescriptor.postSubNo


}