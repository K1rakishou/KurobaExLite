package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser

data class ParsedPostData(
  val parsedPostParts: List<PostCommentParser.TextPart>,
  val parsedPostComment: String,
  val processedPostComment: AnnotatedString,
  val parsedPostSubject: String,
  val processedPostSubject: AnnotatedString,
  val postFooterText: AnnotatedString?,
  val parsedPostDataContext: ParsedPostDataContext
)

data class ParsedPostDataContext(
  val isParsingCatalog: Boolean,
  val revealFullPostComment: Boolean = false,
  val textSpoilerOpenedPositionSet: Set<Int> = emptySet()
) {
  val isParsingThread: Boolean = !isParsingCatalog

  fun maxPostCommentLength(): Int {
    if (revealFullPostComment) {
      return Int.MAX_VALUE
    }

    return if (isParsingCatalog) {
      200
    } else {
      400
    }
  }

}