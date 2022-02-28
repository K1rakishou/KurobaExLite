package com.github.k1rakishou.kurobaexlite.helpers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.github.k1rakishou.kurobaexlite.helpers.filter.BannedWordsHelper
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.SpoilerPosition
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme

class PostCommentApplier {

  fun applyTextPartsToAnnotatedString(
    chanTheme: ChanTheme,
    textParts: List<PostCommentParser.TextPart>,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString {
    val capacity = textParts.sumOf { it.text.length }

    return buildAnnotatedString(capacity = capacity) {
      var totalLength = 0

      for (textPart in textParts) {
        val (text, overflowHappened) = processTextPart(
          chanTheme = chanTheme,
          textPart = textPart,
          parsedPostDataContext = parsedPostDataContext,
          totalLength = totalLength
        )

        append(text)
        totalLength += text.length

        if (overflowHappened) {
          break
        }
      }
    }
  }

  @Suppress("UnnecessaryVariable")
  private fun processTextPart(
    chanTheme: ChanTheme,
    textPart: PostCommentParser.TextPart,
    parsedPostDataContext: ParsedPostDataContext,
    totalLength: Int,
  ): Pair<AnnotatedString, Boolean> {
    var overflowHappened = false

    val resultString = buildAnnotatedString(capacity = textPart.text.length) {
      val (textPartText, overflow) = trimTextPartIfNeeded(
        totalLength = totalLength,
        textPart = textPart,
        parsedPostDataContext = parsedPostDataContext
      )

      val textPartBuilder = StringBuilder(textPartText)
        .apply { processBannedWords(this) }

      overflowHappened = overflow

      if (textPart.spans.isEmpty()) {
        append(textPartBuilder.toString())
      } else {
        processTextPartSpans(
          textPart = textPart,
          chanTheme = chanTheme,
          textPartBuilder = textPartBuilder,
          parsedPostDataContext = parsedPostDataContext,
          totalLength = totalLength
        )
      }

      if (overflow) {
        append("\n")
        append(buildClickToViewFullSpan(chanTheme))
      }
    }

    return resultString to overflowHappened
  }

  private fun AnnotatedString.Builder.processTextPartSpans(
    textPart: PostCommentParser.TextPart,
    chanTheme: ChanTheme,
    textPartBuilder: StringBuilder,
    parsedPostDataContext: ParsedPostDataContext,
    totalLength: Int
  ) {
    for (span in textPart.spans) {
      var bgColor: Color = Color.Unspecified
      var fgColor: Color = Color.Unspecified
      var underline = false
      var linethrough = false
      var annotationTag: String? = null
      var annotationValue: String? = null
      var bold = false

      when (span) {
        is PostCommentParser.TextPartSpan.BgColor -> {
          bgColor = Color(span.color)
        }
        is PostCommentParser.TextPartSpan.FgColor -> {
          fgColor = Color(span.color)
        }
        is PostCommentParser.TextPartSpan.BgColorId -> {
          bgColor = Color(chanTheme.getColorByColorId(span.colorId))
        }
        is PostCommentParser.TextPartSpan.FgColorId -> {
          fgColor = Color(chanTheme.getColorByColorId(span.colorId))
        }
        is PostCommentParser.TextPartSpan.Spoiler -> {
          bgColor = chanTheme.postSpoilerColorCompose

          val shouldRevealSpoiler = matchesOpenedSpoilerPosition(
            startPos = totalLength,
            endPos = totalLength + textPartBuilder.length,
            textSpoilerOpenedPositionSet = parsedPostDataContext.textSpoilerOpenedPositionSet
          )

          fgColor = if (shouldRevealSpoiler) {
            chanTheme.postSpoilerRevealTextColorCompose
          } else {
            chanTheme.postSpoilerColorCompose
          }

          annotationTag = ANNOTATION_POST_SPOILER_TEXT
        }
        is PostCommentParser.TextPartSpan.Linkable -> {
          when (span) {
            is PostCommentParser.TextPartSpan.Linkable.Quote,
            is PostCommentParser.TextPartSpan.Linkable.Board,
            is PostCommentParser.TextPartSpan.Linkable.Search,
            is PostCommentParser.TextPartSpan.Linkable.Url -> {
              underline = true

              if (span is PostCommentParser.TextPartSpan.Linkable.Quote) {
                if (span.postDescriptor.isOP && !span.crossThread) {
                  textPartBuilder
                    .append(" ")
                    .append(OP_POSTFIX)
                }

                if (span.crossThread) {
                  textPartBuilder
                    .append(" ")
                    .append(CROSS_THREAD_POSTFIX)
                }

                if (span.dead) {
                  textPartBuilder
                    .append(" ")
                    .append(DEAD_POSTFIX)

                  linethrough = true
                }

                bold = parsedPostDataContext.markedPostDescriptor == span.postDescriptor
              }

              fgColor = if (span is PostCommentParser.TextPartSpan.Linkable.Url) {
                chanTheme.postLinkColorCompose
              } else {
                chanTheme.postQuoteColorCompose
              }
            }
          }

          if (parsedPostDataContext.isParsingThread) {
            annotationTag = ANNOTATION_POST_LINKABLE
          }

          annotationValue = span.createAnnotationItem()
        }
      }

      append(textPartBuilder.toString())

      val fontWeight = if (bold) {
        FontWeight.Bold
      } else {
        null
      }

      val spanStyle = SpanStyle(
        color = fgColor,
        background = bgColor,
        fontWeight = fontWeight,
        textDecoration = buildTextDecoration(underline, linethrough)
      )

      if (!spanStyle.isEmpty()) {
        addStyle(spanStyle, 0, textPartBuilder.length)
      }

      if (annotationTag != null) {
        addStringAnnotation(
          tag = annotationTag,
          annotation = annotationValue ?: "",
          start = 0,
          end = textPartBuilder.length
        )
      }
    }
  }

  private fun processBannedWords(textPartBuilder: StringBuilder) {
    val matcher = BannedWordsHelper.BANNED_WORDS_PATTERN.matcher(textPartBuilder)

    while (matcher.find()) {
      val start = matcher.start(1)
      val end = matcher.end(1)
      val replacement = (start until end).joinToString(separator = "", transform = { "*" })

      textPartBuilder.replace(start, end, replacement)
    }
  }

  private fun SpanStyle.isEmpty(): Boolean {
    return color.isUnspecified
      && background.isUnspecified
      && (textDecoration == null || textDecoration == TextDecoration.None)
  }

  private fun buildTextDecoration(
    underline: Boolean,
    linethrough: Boolean
  ): TextDecoration? {
    if (!underline && !linethrough) {
      return null
    }

    var textDecoration = TextDecoration.None

    if (underline) {
      textDecoration += TextDecoration.Underline
    }

    if (linethrough) {
      textDecoration += TextDecoration.LineThrough
    }

    return textDecoration
  }

  private fun trimTextPartIfNeeded(
    totalLength: Int,
    textPart: PostCommentParser.TextPart,
    parsedPostDataContext: ParsedPostDataContext
  ): Pair<String, Boolean> {
    val maxLength = parsedPostDataContext.maxPostCommentLength()
    if (maxLength == Int.MAX_VALUE || parsedPostDataContext.isParsingThread) {
      return textPart.text to false
    }

    if (totalLength + textPart.text.length <= maxLength) {
      return textPart.text to false
    }

    val count = (totalLength + textPart.text.length)
      .coerceAtMost(maxLength)

    val resultText = buildString(capacity = count + ELLIPSIZE.length) {
      append(textPart.text.take(count))
      append(ELLIPSIZE)
    }

    return resultText to true
  }

  private fun buildClickToViewFullSpan(
    chanTheme: ChanTheme
  ) : AnnotatedString {
    return buildAnnotatedString(capacity = CLICK_TO_EXPAND.length) {
      append(CLICK_TO_EXPAND)

      addStyle(
        style = SpanStyle(
          color = chanTheme.postLinkColorCompose,
          textDecoration = TextDecoration.Underline
        ),
        start = 0,
        end = length
      )

      addStringAnnotation(
        tag = ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG,
        annotation = "",
        start = 0,
        end = length
      )
    }
  }

  private fun matchesOpenedSpoilerPosition(
    startPos: Int,
    endPos: Int,
    textSpoilerOpenedPositionSet: Set<SpoilerPosition>
  ): Boolean {
    for (position in textSpoilerOpenedPositionSet) {
      if (position.start == startPos && position.end == endPos) {
        return true
      }
    }

    return false
  }

  companion object {
    private const val ELLIPSIZE = "..."
    private const val CLICK_TO_EXPAND = "[Click to expand]"

    const val ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG = "[click_to_view_full_comment]"
    const val ANNOTATION_POST_LINKABLE = "[post_linkable]"
    const val ANNOTATION_POST_SPOILER_TEXT = "[spoiler_text]"

    private const val CROSS_THREAD_POSTFIX = "->"
    private const val OP_POSTFIX = "(OP)"
    private const val DEAD_POSTFIX = "(DEAD)"

    val ALL_TAGS = mutableSetOf(
      ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG,
      ANNOTATION_POST_LINKABLE,
      ANNOTATION_POST_SPOILER_TEXT
    )
  }

}