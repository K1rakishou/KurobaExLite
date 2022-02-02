package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class OriginalPostData(
  postDescriptor: PostDescriptor,
  postSubjectUnparsed: String,
  postCommentUnparsed: String,
  images: List<PostImageData>?,
  val threadRepliesTotal: Int?,
  val threadImagesTotal: Int?,
  _parsedPostData: ParsedPostData?
) : PostData(
  postDescriptor = postDescriptor,
  postSubjectUnparsed = postSubjectUnparsed,
  postCommentUnparsed = postCommentUnparsed,
  images = images,
  _parsedPostData = _parsedPostData
)