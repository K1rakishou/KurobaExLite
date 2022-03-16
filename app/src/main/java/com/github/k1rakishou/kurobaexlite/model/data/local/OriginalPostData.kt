package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class OriginalPostData(
  originalPostOrder: Int,
  postDescriptor: PostDescriptor,
  postSubjectUnparsed: String,
  postCommentUnparsed: String,
  timeMs: Long?,
  images: List<PostImageData>?,
  threadRepliesTotal: Int?,
  threadImagesTotal: Int?,
  threadPostersTotal: Int?,
  lastModified: Long?,
  parsedPostData: ParsedPostData?
) : PostData(
  originalPostOrder = originalPostOrder,
  postDescriptor = postDescriptor,
  postSubjectUnparsed = postSubjectUnparsed,
  postCommentUnparsed = postCommentUnparsed,
  timeMs = timeMs,
  images = images,
  threadRepliesTotal = threadRepliesTotal,
  threadImagesTotal = threadImagesTotal,
  threadPostersTotal = threadPostersTotal,
  lastModified = lastModified,
  parsedPostData = parsedPostData
) {

  override fun copy(
    originalPostOrder: Int,
    postDescriptor: PostDescriptor,
    postSubjectUnparsed: String,
    postCommentUnparsed: String,
    timeMs: Long?,
    images: List<PostImageData>?,
    threadRepliesTotal: Int?,
    threadImagesTotal: Int?,
    threadPostersTotal: Int?,
    lastModified: Long?,
    parsedPostData: ParsedPostData?
  ): PostData {
    return OriginalPostData(
      originalPostOrder = originalPostOrder,
      postDescriptor = postDescriptor,
      postSubjectUnparsed = postSubjectUnparsed,
      postCommentUnparsed = postCommentUnparsed,
      timeMs = timeMs,
      images = images,
      threadRepliesTotal = threadRepliesTotal,
      threadImagesTotal = threadImagesTotal,
      threadPostersTotal = threadPostersTotal,
      lastModified = lastModified,
      parsedPostData = parsedPostData
    )
  }

}