package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.MurmurHashUtils
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

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

// Options used to parse this post
data class ParsedPostDataContext(
  val isParsingCatalog: Boolean,
  val revealFullPostComment: Boolean = false,
  val textSpoilerOpenedPositionSet: Set<SpoilerPosition> = emptySet(),
  val markedPostDescriptor: PostDescriptor? = null
) {
  val isParsingThread: Boolean = !isParsingCatalog

  fun maxPostCommentLength(): Int {
    if (revealFullPostComment) {
      return Int.MAX_VALUE
    }

    if (isParsingCatalog) {
      return 200
    }

    return Int.MAX_VALUE
  }

  fun murmurhash(): MurmurHashUtils.Murmur3Hash {
    return MurmurHashUtils.murmurhash3_x64_128(isParsingCatalog)
      .combine(MurmurHashUtils.murmurhash3_x64_128(revealFullPostComment))
      .combine(MurmurHashUtils.murmurhash3_x64_128(textSpoilerOpenedPositionSet))
      .combine(MurmurHashUtils.murmurhash3_x64_128(markedPostDescriptor))
  }

}

data class SpoilerPosition(val start: Int, val end: Int)