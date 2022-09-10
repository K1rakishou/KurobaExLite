package com.github.k1rakishou.kurobaexlite.model.data.local

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.helpers.hash.MurmurHashUtils
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPart
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

@Immutable
data class ParsedPostData(
  val parsedPostParts: List<TextPart>,
  val parsedPostComment: String,
  val parsedPostSubject: String,
  val processedPostComment: AnnotatedString,
  val processedPostSubject: AnnotatedString,
  val postFooterText: AnnotatedString?,
  val isPostMarkedAsMine: Boolean,
  val isReplyToPostMarkedAsMine: Boolean,
  val parsedPostDataContext: ParsedPostDataContext
) {

  fun murmurhash(): Murmur3Hash {
    return MurmurHashUtils.murmurhash3_x64_128(parsedPostParts)
      .combine(MurmurHashUtils.murmurhash3_x64_128(parsedPostComment))
      .combine(MurmurHashUtils.murmurhash3_x64_128(processedPostComment))
      .combine(MurmurHashUtils.murmurhash3_x64_128(parsedPostSubject))
      .combine(MurmurHashUtils.murmurhash3_x64_128(processedPostSubject))
      .combine(MurmurHashUtils.murmurhash3_x64_128(postFooterText))
      .combine(MurmurHashUtils.murmurhash3_x64_128(isPostMarkedAsMine))
      .combine(MurmurHashUtils.murmurhash3_x64_128(isReplyToPostMarkedAsMine))
      .combine(parsedPostDataContext.murmurhash())
  }

}

// Options used to parse this post
@Immutable
data class ParsedPostDataContext(
  val isParsingCatalog: Boolean,
  val postViewMode: PostViewMode,
  val revealFullPostComment: Boolean = false,
  val textSpoilerOpenedPositionSet: Set<SpoilerPosition> = emptySet(),
  val highlightedPostDescriptor: PostDescriptor? = null
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

  fun murmurhash(): Murmur3Hash {
    return MurmurHashUtils.murmurhash3_x64_128(isParsingCatalog)
      .combine(MurmurHashUtils.murmurhash3_x64_128(postViewMode))
      .combine(MurmurHashUtils.murmurhash3_x64_128(revealFullPostComment))
      .combine(MurmurHashUtils.murmurhash3_x64_128(textSpoilerOpenedPositionSet))
      .combine(MurmurHashUtils.murmurhash3_x64_128(highlightedPostDescriptor))
  }

}

@Immutable
data class SpoilerPosition(val start: Int, val end: Int)