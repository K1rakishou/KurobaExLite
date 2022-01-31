package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.annotation.GuardedBy
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PostData(
  val postDescriptor: PostDescriptor,
  val postSubjectUnparsed: String,
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
  val postSubjectParsedAndProcessed: AnnotatedString?
    get() = _parsedPostData?.processedPostSubject
  val parsedPostDataContext: ParsedPostDataContext?
    get() = _parsedPostData?.parsedPostDataContext

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

  fun formatToolbarTitle(catalogMode: Boolean): String? {
    if (catalogMode) {
      return "${postDescriptor.siteKeyActual}/${postDescriptor.boardCode}/"
    }

    val parsedPostData = _parsedPostData
      ?: return null

    if (parsedPostData.parsedPostSubject.isNotNullNorBlank()) {
      return parsedPostData.parsedPostSubject
    }

    return parsedPostData.parsedPostComment.take(64)
  }

}