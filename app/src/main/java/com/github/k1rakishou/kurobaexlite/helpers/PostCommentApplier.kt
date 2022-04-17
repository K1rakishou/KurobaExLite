package com.github.k1rakishou.kurobaexlite.helpers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPost
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPostType
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.SpoilerPosition
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine

class PostCommentApplier {

  suspend fun applyTextPartsToAnnotatedString(
    chanTheme: ChanTheme,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    textParts: List<PostCommentParser.TextPart>,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString {
    val capacity = textParts.sumOf { it.text.length }

    return buildAnnotatedString(capacity = capacity) {
      var totalLength = 0

      for (textPart in textParts) {
        val (text, overflowHappened) = processTextPart(
          markedPosts = markedPosts,
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
  private suspend fun processTextPart(
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
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

      append(textPartText)
      overflowHappened = overflow

      if (textPart.spans.isNotEmpty()) {
        processTextPartSpans(
          markedPosts = markedPosts,
          spans = textPart.spans,
          chanTheme = chanTheme,
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
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    spans: List<PostCommentParser.TextPartSpan>,
    chanTheme: ChanTheme,
    parsedPostDataContext: ParsedPostDataContext,
    totalLength: Int
  ) {
    for (span in spans) {
      var bgColor: Color = Color.Unspecified
      var fgColor: Color = Color.Unspecified
      var underline = false
      var linethrough = false
      var annotationTag: String? = null
      var annotationValue: String? = null
      var bold = false

      var start: Int? = null
      var end: Int? = null

      when (span) {
        is PostCommentParser.TextPartSpan.PartialSpan -> {
          start = span.start
          end = span.end
          underline = true

          when (span.linkSpan) {
            is PostCommentParser.TextPartSpan.Linkable.Url -> {
              fgColor = chanTheme.postLinkColorCompose
            }
            is PostCommentParser.TextPartSpan.BgColor,
            is PostCommentParser.TextPartSpan.BgColorId,
            is PostCommentParser.TextPartSpan.FgColor,
            is PostCommentParser.TextPartSpan.FgColorId,
            is PostCommentParser.TextPartSpan.Linkable.Board,
            is PostCommentParser.TextPartSpan.Linkable.Quote,
            is PostCommentParser.TextPartSpan.Linkable.Search,
            is PostCommentParser.TextPartSpan.PartialSpan,
            PostCommentParser.TextPartSpan.Spoiler -> {
              error("${span.linkSpan::class.java.simpleName} is not supported as a partial span")
            }
          }

          if (parsedPostDataContext.isParsingThread) {
            annotationTag = ANNOTATION_POST_LINKABLE
          }

          annotationValue = span.linkSpan.createAnnotationItem()
        }
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
            endPos = totalLength + this.length,
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
                  this.append(" ")
                  this.append(OP_POSTFIX)
                }

                if (span.crossThread) {
                  this.append(" ")
                  this.append(CROSS_THREAD_POSTFIX)
                }

                if (span.dead) {
                  this.append(" ")
                  this.append(DEAD_POSTFIX)

                  linethrough = true
                }

                val markedPostInfoSet = markedPosts[span.postDescriptor]
                if (markedPostInfoSet != null && markedPostInfoSet.isNotEmpty()) {
                  markedPostInfoSet.forEach { markedPost ->
                    when (markedPost.markedPostType) {
                      MarkedPostType.MyPost -> {
                        this.append(" ")
                        this.append(YOU_POSTFIX)
                      }
                    }
                  }
                }

                bold = parsedPostDataContext.highlightedPostDescriptor == span.postDescriptor
              }

              fgColor = if (span is PostCommentParser.TextPartSpan.Linkable.Url) {
                chanTheme.postLinkColorCompose
              } else if (
                span is PostCommentParser.TextPartSpan.Linkable.Quote
                && parsedPostDataContext.highlightedPostDescriptor == span.postDescriptor
              ) {
                ThemeEngine.manipulateColor(chanTheme.postQuoteColorCompose, 0.7f)
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
        addStyle(
          style = spanStyle,
          start = start ?: 0,
          end = end ?: this.length
        )
      }

      if (annotationTag != null) {
        addStringAnnotation(
          tag = annotationTag,
          annotation = annotationValue ?: "",
          start = start ?: 0,
          end = end ?: this.length
        )
      }
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

    private const val CROSS_THREAD_POSTFIX = '\u2192'
    private const val OP_POSTFIX = "(OP)"
    private const val DEAD_POSTFIX = "(DEAD)"
    private const val YOU_POSTFIX = "(You)"

    val ALL_TAGS = mutableSetOf(
      ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG,
      ANNOTATION_POST_LINKABLE,
      ANNOTATION_POST_SPOILER_TEXT
    )
  }

}