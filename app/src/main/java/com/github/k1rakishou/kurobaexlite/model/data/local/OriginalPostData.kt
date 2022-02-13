package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class OriginalPostData(
  postDescriptor: PostDescriptor,
  postSubjectUnparsed: String,
  postCommentUnparsed: String,
  images: List<PostImageData>?,
  threadRepliesTotal: Int?,
  threadImagesTotal: Int?,
  threadPostersTotal: Int?,
  parsedPostData: ParsedPostData?
) : PostData(
  postDescriptor = postDescriptor,
  postSubjectUnparsed = postSubjectUnparsed,
  postCommentUnparsed = postCommentUnparsed,
  images = images,
  threadRepliesTotal = threadRepliesTotal,
  threadImagesTotal = threadImagesTotal,
  threadPostersTotal = threadPostersTotal,
  parsedPostData = parsedPostData
) {

  override fun copy(
    postDescriptor: PostDescriptor,
    postSubjectUnparsed: String,
    postCommentUnparsed: String,
    images: List<PostImageData>?,
    threadRepliesTotal: Int?,
    threadImagesTotal: Int?,
    threadPostersTotal: Int?,
    parsedPostData: ParsedPostData?
  ): PostData {
    return OriginalPostData(
      postDescriptor = postDescriptor,
      postSubjectUnparsed = postSubjectUnparsed,
      postCommentUnparsed = postCommentUnparsed,
      images = images,
      threadRepliesTotal = threadRepliesTotal,
      threadImagesTotal = threadImagesTotal,
      threadPostersTotal = threadPostersTotal,
      parsedPostData = parsedPostData
    )
  }

}