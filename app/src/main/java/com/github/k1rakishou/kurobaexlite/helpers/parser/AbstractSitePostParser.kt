package com.github.k1rakishou.kurobaexlite.helpers.parser

import androidx.annotation.CallSuper
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlTag
import com.github.k1rakishou.kurobaexlite.helpers.html.StaticHtmlColorRepository
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import logcat.LogPriority
import logcat.logcat
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import java.util.*

abstract class AbstractSitePostParser(
  private val staticHtmlColorRepository: StaticHtmlColorRepository
) {

  fun parseHtmlNode(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParser.PostCommentParserContext
  ) {
    when (val tagName = htmlTag.tagName) {
      "br" -> parseNewLineTag(childTextParts)
      "p" -> parseParagraphTag(childTextParts)
      "s" -> parseStrikethroughTag(childTextParts)
      "b",
      "strong" -> parseStrongTag(childTextParts)
      "em" -> parseEmphasizedTag(childTextParts)
      "sup" -> parseSuperscriptTag(childTextParts)
      "sub" -> parseSubscriptTag(childTextParts)
      "span" -> parseSpanTag(htmlTag, childTextParts, postDescriptor)
      "ins" -> parseInsTag(htmlTag, childTextParts, postDescriptor)
      "a" -> parseLinkTag(htmlTag, childTextParts, postDescriptor)
      "ul" -> { /**no-op*/ }
      "tr" -> parseTrTag(htmlTag, childTextParts, postDescriptor, parserContext)
      "li" -> parseLiTag(htmlTag, childTextParts, postDescriptor, parserContext)
      "pre" -> parsePreTag(htmlTag, childTextParts, postDescriptor, parserContext)
      "wbr" -> {
        error("<wbr> tags should all be removed during the HTML parsing stage. This is most likely a HTML parser bug.")
      }
      else -> {
        if (tagName.startsWith("h", ignoreCase = true) && tagName.length == 2) {
          parseHeadingTag(
            htmlTag = htmlTag,
            childTextParts = childTextParts,
            postDescriptor = postDescriptor,
            parserContext = parserContext
          )

          return
        }

        logcat(priority = LogPriority.WARN, tag = TAG) {
          "Unsupported tag with name '${htmlTag.tagName}' found. " +
            "(postDescriptor=$postDescriptor, htmlTag=$htmlTag)"
        }
      }
    }
  }

  fun postProcessHtmlNode(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParser.PostCommentParserContext
  ) {
    parseAnyTagStyleAttribute(htmlTag, childTextParts)
    parseAnyTagSizeAttribute(htmlTag, childTextParts)
    parseAnyTagColorAttribute(htmlTag, childTextParts)
  }

  @CallSuper
  open fun parsePreTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParser.PostCommentParserContext
  ) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Monospace)
    }
  }

  @CallSuper
  open fun parseInsTag(htmlTag: HtmlTag, childTextParts: MutableList<TextPartMut>, postDescriptor: PostDescriptor) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Underline)
    }
  }

  @CallSuper
  open fun parseHeadingTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParser.PostCommentParserContext
  ) {
    val headingSize = htmlTag.tagName.getOrNull(1)
      ?.digitToIntOrNull()
      ?.takeIf { size -> size in 0..5 }
      ?: return

    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Heading(headingSize))
    }
  }

  @CallSuper
  open fun parseTrTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParser.PostCommentParserContext
  ) {
    if (parserContext.isInsideTableTag) {
      childTextParts += TextPartMut("\n")
    }
  }

  @CallSuper
  open fun parseLiTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor,
    parserContext: PostCommentParser.PostCommentParserContext
  ) {
    if (parserContext.isInsideUlTag) {
      val text = buildString {
        repeat(parserContext.ulTagsCounter.coerceAtMost(5)) {
          append("  ")
        }

        append("•")
        append(" ")
      }

      childTextParts.add(0, TextPartMut(text))
    }

    childTextParts += TextPartMut("\n")
  }

  @CallSuper
  open fun parseSubscriptTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Subscript)
    }
  }

  @CallSuper
  open fun parseSuperscriptTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Superscript)
    }
  }

  @CallSuper
  open fun parseEmphasizedTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Italic)
    }
  }

  @CallSuper
  open fun parseStrongTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Bold)
    }
  }

  abstract fun parseNewLineTag(childTextParts: MutableList<TextPartMut>)
  abstract fun parseParagraphTag(childTextParts: MutableList<TextPartMut>)
  abstract fun parseStrikethroughTag(childTextParts: MutableList<TextPartMut>)

  @CallSuper
  open fun parseSpanTag(htmlTag: HtmlTag, childTextParts: MutableList<TextPartMut>, postDescriptor: PostDescriptor) {
    if (htmlTag.hasClass("u")) {
      for (childTextPart in childTextParts) {
        childTextPart.spans.add(TextPartSpan.Underline)
      }
    }
  }

  abstract fun parseLinkTag(htmlTag: HtmlTag, childTextParts: MutableList<TextPartMut>, postDescriptor: PostDescriptor)
  abstract fun parseLinkable(className: String?, href: String, postDescriptor: PostDescriptor): TextPartSpan.Linkable?
  abstract fun postProcessTextParts(textPartMut: TextPartMut): TextPartMut

  protected fun mergeChildTextPartsIntoOne(
    childTextParts: List<TextPartMut>
  ): TextPartMut {
    val totalText = StringBuilder(childTextParts.sumOf { it.text.length })
    val totalSpans = mutableListWithCap<TextPartSpan>(childTextParts.sumOf { it.spans.size })

    for (inputChildTextPart in childTextParts) {
      totalText.append(inputChildTextPart.text)
      totalSpans.addAll(inputChildTextPart.spans)
    }

    return TextPartMut(
      text = totalText.toString(),
      spans = totalSpans
    )
  }

  private fun parseAnyTagColorAttribute(htmlTag: HtmlTag, childTextParts: MutableList<TextPartMut>) {
    val colorName = htmlTag.attrUnescapedOrNull("color")
      ?: return

    val color = staticHtmlColorRepository.parseColor(colorName)
      ?: return

    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.FgColor(color))
    }
  }

  private fun parseAnyTagSizeAttribute(htmlTag: HtmlTag, childTextParts: MutableList<TextPartMut>) {
    val sizeAttribute = htmlTag.attrUnescapedOrNull("size")
      ?: return

    val minSize = TextPartSpan.FontSize.MIN_FONT_SIZE_INCREMENT
    val maxSize = TextPartSpan.FontSize.MAX_FONT_SIZE_INCREMENT

    val fontSize = sizeAttribute.toIntOrNull()
      .takeIf { size -> size in minSize..maxSize }
      ?: return

    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.FontSize(fontSize))
    }
  }

  private fun parseAnyTagStyleAttribute(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>
  ) {
    val styleAttribute = htmlTag.attrUnescapedOrNull("style")
      ?: return

    val parameters = styleAttribute.split(';')

    for (parameter in parameters) {
      if (parameter.startsWith("color:")) {
        val colorName = parameter.split(":").getOrNull(1)?.trim() ?: continue
        val colorRgb = staticHtmlColorRepository.parseColor(colorName) ?: continue

        for (childTextPart in childTextParts) {
          childTextPart.spans.add(TextPartSpan.FgColor(colorRgb))
        }
      } else if (parameter.startsWith("background-color:")) {
        val colorName = parameter.split(":").getOrNull(1)?.trim() ?: continue
        val colorRgb = staticHtmlColorRepository.parseColor(colorName) ?: continue

        for (childTextPart in childTextParts) {
          childTextPart.spans.add(TextPartSpan.BgColor(colorRgb))
        }
      }
    }
  }

  companion object {
    private const val TAG = "AbstractSitePostParser"

    val LINK_EXTRACTOR = LinkExtractor.builder()
      .linkTypes(EnumSet.of(LinkType.URL))
      .build()
  }

}