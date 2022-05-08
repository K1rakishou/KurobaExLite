package com.github.k1rakishou.kurobaexlite.helpers.parser

import androidx.compose.runtime.Immutable

data class TextPartMut(
  val text: String,
  val spans: MutableList<TextPartSpan> = mutableListOf()
) {
  fun isLinkable(): Boolean {
    for (span in spans) {
      if (span is TextPartSpan.Linkable) {
        return true
      }
    }

    return false
  }

  fun toTextPartWithSortedSpans(): TextPart {
    spans.sortBy { textPartSpan -> textPartSpan.priority() }

    return TextPart(text, spans)
  }
}

@Immutable
data class TextPart(
  val text: String,
  val spans: List<TextPartSpan> = emptyList()
) {
  fun isLinkable(): Boolean {
    for (span in spans) {
      if (span is TextPartSpan.Linkable) {
        return true
      }
    }

    return false
  }
}