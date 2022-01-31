package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class OriginalPostData(
  postDescriptor: PostDescriptor,
  postSubjectUnparsed: String,
  postCommentUnparsed: String,
  images: List<PostImageData>?,
  val threadRepliesTotal: Int?,
  val threadImagesTotal: Int?
) : PostData(
  postDescriptor,
  postSubjectUnparsed,
  postCommentUnparsed,
  images
)