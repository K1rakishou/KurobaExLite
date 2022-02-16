package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.MurmurHashUtils
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser

data class ParsedPostData(
  val parsedPostParts: List<PostCommentParser.TextPart>,
  val parsedPostComment: String,
  val parsedPostSubject: String,
  val processedPostComment: AnnotatedString,
  val processedPostSubject: AnnotatedString,
  val postFooterText: AnnotatedString?,
  val parsedPostDataContext: ParsedPostDataContext
) {

  fun murmurhash(): MurmurHashUtils.Murmur3Hash {
    return MurmurHashUtils.murmurhash3_x64_128(parsedPostParts)
      .combine(MurmurHashUtils.murmurhash3_x64_128(parsedPostComment))
      .combine(MurmurHashUtils.murmurhash3_x64_128(processedPostComment))
      .combine(MurmurHashUtils.murmurhash3_x64_128(parsedPostSubject))
      .combine(MurmurHashUtils.murmurhash3_x64_128(processedPostSubject))
      .combine(MurmurHashUtils.murmurhash3_x64_128(postFooterText))
      .combine(parsedPostDataContext.murmurhash())
  }

}

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

  fun murmurhash(): MurmurHashUtils.Murmur3Hash {
    return MurmurHashUtils.murmurhash3_x64_128(isParsingCatalog)
      .combine(MurmurHashUtils.murmurhash3_x64_128(revealFullPostComment))
      .combine(MurmurHashUtils.murmurhash3_x64_128(textSpoilerOpenedPositionSet))
  }

}