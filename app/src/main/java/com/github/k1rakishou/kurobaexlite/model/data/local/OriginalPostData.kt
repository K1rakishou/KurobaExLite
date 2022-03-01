package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class OriginalPostData(
  postIndex: Int,
  postDescriptor: PostDescriptor,
  postSubjectUnparsed: String,
  postCommentUnparsed: String,
  images: List<PostImageData>?,
  threadRepliesTotal: Int?,
  threadImagesTotal: Int?,
  threadPostersTotal: Int?,
  parsedPostData: ParsedPostData?
) : PostData(
  postIndex = postIndex,
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
    postIndex: Int,
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
      postIndex = postIndex,
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