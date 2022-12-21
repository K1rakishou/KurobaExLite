package com.github.k1rakishou.kurobaexlite.helpers.parser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.util.createAnnotationItem
import com.github.k1rakishou.kurobaexlite.helpers.util.findAllOccurrences
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPost
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPostType
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.local.SpoilerPosition
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine

class PostCommentApplier(
  private val appSettings: AppSettings,
  private val appResources: AppResources
) {

  suspend fun applyTextPartsToAnnotatedString(
    chanTheme: ChanTheme,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    textParts: List<TextPart>,
    parsedPostDataContext: ParsedPostDataContext
  ): AnnotatedString {
    val defaultPostCommentFontSize = appSettings.calculateFontSizeInPixels(16)
    val capacity = textParts.sumOf { it.text.length }

    return buildAnnotatedString(capacity = capacity) {
      var totalLength = 0

      for (textPart in textParts) {
        val (text, overflowHappened) = processTextPart(
          defaultPostCommentFontSize = defaultPostCommentFontSize,
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

  fun markOrUnmarkSearchQuery(
    chanTheme: ChanTheme,
    searchQuery: String?,
    minQueryLength: Int,
    string: AnnotatedString
  ): Pair<Boolean, AnnotatedString> {
    val occurrences = string.text.findAllOccurrences(searchQuery, minQueryLength)
    if (occurrences.isEmpty()) {
      return false to removeSearchQuery(string)
    }

    val oldSpanStyles = string.spanStyles
      .filter { spanStyle -> spanStyle.tag != SEARCH_QUERY_SPAN }

    val newSpanStyles = mutableListWithCap<AnnotatedString.Range<SpanStyle>>(oldSpanStyles.size + occurrences.size)
    newSpanStyles.addAll(oldSpanStyles)

    val bgColor = chanTheme.accentColor
    val fgColor = if (ThemeEngine.isDarkColor(bgColor)) {
      Color.White
    } else {
      Color.Black
    }

    occurrences.forEach { range ->
      newSpanStyles += AnnotatedString.Range<SpanStyle>(
        item = SpanStyle(
          color = fgColor,
          background = bgColor,
          fontWeight = FontWeight.Bold
        ),
        start = range.first,
        end = range.last,
        tag = SEARCH_QUERY_SPAN
      )
    }

    return true to AnnotatedString(
      text = string.text,
      spanStyles = newSpanStyles,
      paragraphStyles = string.paragraphStyles
    )
  }

  private fun removeSearchQuery(string: AnnotatedString): AnnotatedString {
    val newSpanStyles = string.spanStyles
      .filter { spanStyle -> spanStyle.tag != SEARCH_QUERY_SPAN }

    if (newSpanStyles.size == string.spanStyles.size) {
      return string
    }

    return AnnotatedString(
      text = string.text,
      spanStyles = newSpanStyles,
      paragraphStyles = string.paragraphStyles
    )
  }

  @Suppress("UnnecessaryVariable")
  private fun processTextPart(
    defaultPostCommentFontSize: Int,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    chanTheme: ChanTheme,
    textPart: TextPart,
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
          defaultPostCommentFontSize = defaultPostCommentFontSize,
          markedPosts = markedPosts,
          spans = textPart.spans,
          chanTheme = chanTheme,
          parsedPostDataContext = parsedPostDataContext,
          totalLength = totalLength
        )
      }

      if (textPart.spans.isEmpty() || textPart.spans.all { textPartSpan -> textPartSpan.isPartialSpan }) {
        addStyle(
          style = SpanStyle(fontSize = defaultPostCommentFontSize.sp),
          start = 0,
          end = this.length
        )
      }

      if (overflow) {
        append("\n")
        append(buildClickToViewFullSpan(defaultPostCommentFontSize, chanTheme))
      }
    }

    return resultString to overflowHappened
  }

  private fun AnnotatedString.Builder.processTextPartSpans(
    defaultPostCommentFontSize: Int,
    markedPosts: Map<PostDescriptor, Set<MarkedPost>>,
    spans: List<TextPartSpan>,
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
      var italic = false
      var script: Script? = null
      var currentFontSize = defaultPostCommentFontSize

      var start: Int? = null
      var end: Int? = null

      when (span) {
        is TextPartSpan.PartialSpan -> {
          start = span.start
          end = span.end
          underline = true

          when (span.linkSpan) {
            is TextPartSpan.Linkable.Url -> {
              fgColor = chanTheme.postLinkColor
            }
            else -> {
              error("${span.linkSpan::class.java.simpleName} is not supported as a partial span")
            }
          }

          if (parsedPostDataContext.isParsingThread) {
            annotationTag = ANNOTATION_POST_LINKABLE
          }

          annotationValue = span.linkSpan.createAnnotationItem()
        }
        is TextPartSpan.BgColor -> {
          bgColor = Color(span.color)
        }
        is TextPartSpan.FgColor -> {
          fgColor = Color(span.color)
        }
        is TextPartSpan.BgColorId -> {
          bgColor = chanTheme.getColorByColorId(span.colorId)
        }
        is TextPartSpan.FgColorId -> {
          fgColor = chanTheme.getColorByColorId(span.colorId)
        }
        is TextPartSpan.Heading -> {
          currentFontSize = span.calculateNewFontSize(currentFontSize)
        }
        is TextPartSpan.FontSize -> {
          currentFontSize = span.calculateNewFontSize(currentFontSize)
        }
        is TextPartSpan.Spoiler -> {
          bgColor = chanTheme.postSpoilerColor

          val shouldRevealSpoiler = matchesOpenedSpoilerPosition(
            startPos = totalLength,
            endPos = totalLength + this.length,
            textSpoilerOpenedPositionSet = parsedPostDataContext.textSpoilerOpenedPositionSet
          )

          fgColor = if (shouldRevealSpoiler) {
            chanTheme.postSpoilerRevealTextColor
          } else {
            chanTheme.postSpoilerColor
          }

          annotationTag = ANNOTATION_POST_SPOILER_TEXT
        }
        is TextPartSpan.Underline -> {
          underline = true
        }
        is TextPartSpan.Linethrough -> {
          linethrough = true
        }
        is TextPartSpan.Bold -> {
          bold = true
        }
        is TextPartSpan.Italic -> {
          italic = true
        }
        is TextPartSpan.Superscript -> {
          script = Script.Super
        }
        is TextPartSpan.Subscript -> {
          script = Script.Sub
        }
        is TextPartSpan.Linkable -> {
          when (span) {
            is TextPartSpan.Linkable.Quote,
            is TextPartSpan.Linkable.Board,
            is TextPartSpan.Linkable.Search,
            is TextPartSpan.Linkable.Url -> {
              underline = true

              if (span is TextPartSpan.Linkable.Quote) {
                if (span.dead) {
                  this.append(" ")
                  this.append(DEAD_POSTFIX)

                  linethrough = true
                }

                if (span.postDescriptor.isOP && !span.crossThread) {
                  this.append(" ")
                  this.append(OP_POSTFIX)
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

                if (span.crossThread) {
                  this.append(" ")
                  this.append(CROSS_THREAD_POSTFIX)
                }

                bold = parsedPostDataContext.highlightedPostDescriptor == span.postDescriptor
              }

              fgColor = if (span is TextPartSpan.Linkable.Url) {
                chanTheme.postLinkColor
              } else if (
                span is TextPartSpan.Linkable.Quote
                && parsedPostDataContext.highlightedPostDescriptor == span.postDescriptor
              ) {
                ThemeEngine.manipulateColor(chanTheme.postQuoteColor, 0.7f)
              } else {
                chanTheme.postQuoteColor
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
        FontWeight.ExtraBold
      } else {
        null
      }

      val fontStyle = if (italic) {
        FontStyle.Italic
      } else {
        null
      }

      val baselineShift = when (script) {
        Script.Sub -> BaselineShift.Subscript
        Script.Super -> BaselineShift.Superscript
        null -> null
      }

      val spanStyle = SpanStyle(
        color = fgColor,
        fontSize = currentFontSize.sp,
        background = bgColor,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        baselineShift = baselineShift,
        textDecoration = buildTextDecoration(underline, linethrough)
      )

      addStyle(
        style = spanStyle,
        start = start ?: 0,
        end = end ?: this.length
      )

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

  private fun buildTextDecoration(
    underline: Boolean,
    linethrough: Boolean,
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
    textPart: TextPart,
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

    return when (parsedPostDataContext.postViewMode) {
      PostViewMode.List -> resultText to true
      PostViewMode.Grid -> resultText to false
    }
  }

  private fun buildClickToViewFullSpan(
    defaultPostCommentFontSize: Int,
    chanTheme: ChanTheme
  ) : AnnotatedString {
    return buildAnnotatedString(capacity = CLICK_TO_EXPAND.length) {
      append(CLICK_TO_EXPAND)

      addStyle(
        style = SpanStyle(
          color = chanTheme.postLinkColor,
          fontSize = defaultPostCommentFontSize.sp,
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

  private enum class Script {
    Sub,
    Super
  }

  companion object {
    private const val ELLIPSIZE = "..."
    private const val CLICK_TO_EXPAND = "[Click to expand]"

    private const val SEARCH_QUERY_SPAN = "search_query_span"

    const val ANNOTATION_CLICK_TO_VIEW_FULL_COMMENT_TAG = "[click_to_view_full_comment]"
    const val ANNOTATION_POST_LINKABLE = "[post_linkable]"
    const val ANNOTATION_POST_SPOILER_TEXT = "[spoiler_text]"

    private const val CROSS_THREAD_POSTFIX = "(CT) \u2192"
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