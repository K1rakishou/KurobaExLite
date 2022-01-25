package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.annotation.GuardedBy
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PostData(
  val postDescriptor: PostDescriptor,
  val postCommentUnparsed: String,
  val images: List<PostImageData>?
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  @Volatile
  private var _parsedPostData: ParsedPostData? = null

  val postNo: Long
    get() = postDescriptor.postNo
  val postSubNo: Long?
    get() = postDescriptor.postSubNo

  val postCommentParsedAndProcessed: AnnotatedString?
    get() = _parsedPostData?.processedPostComment

  suspend fun getOrCalculateParsedPostParts(
    calcFunc: suspend () -> ParsedPostData
  ): ParsedPostData {
    return mutex.withLock {
      if (_parsedPostData != null) {
        return@withLock _parsedPostData!!
      }

      _parsedPostData = calcFunc()
      return@withLock _parsedPostData!!
    }
  }

  class ParsedPostData(
    val parsedPostParts: List<PostCommentParser.TextPart>,
    val processedPostComment: AnnotatedString
  )

}