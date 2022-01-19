package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class PostData(
  val postDescriptor: PostDescriptor,
  val postCommentUnparsed: String?,
  val images: List<PostImageData>?
)