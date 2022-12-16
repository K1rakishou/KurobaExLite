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
    when (htmlTag.tagName) {
      "br" -> parseNewLineTag(childTextParts)
      "p" -> parseParagraphTag(childTextParts)
      "s" -> parseStrikethroughTag(childTextParts)
      "b",
      "strong" -> parseStrongTag(childTextParts)
      "em" -> parseEmphasizedTag(childTextParts)
      "sup" -> parseSuperscriptTag(childTextParts)
      "sub" -> parseSubscriptTag(childTextParts)
      "span" -> parseSpanTag(htmlTag, childTextParts, postDescriptor)
      "a" -> parseLinkTag(htmlTag, childTextParts, postDescriptor)
      "ul" -> { /**no-op*/ }
      "li" -> parseLiTag(htmlTag, childTextParts, postDescriptor, parserContext)
      "wbr" -> {
        error("<wbr> tags should all be removed during the HTML parsing stage. This is most likely a HTML parser bug.")
      }
      else -> {
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
        val colorName = parameter.split(":").getOrNull(1) ?: continue
        val colorRgb = staticHtmlColorRepository.getColorValueByHtmlColorName(colorName) ?: continue

        for (childTextPart in childTextParts) {
          childTextPart.spans.add(TextPartSpan.FgColor(colorRgb))
        }
      }
    }
  }

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

  open fun parseSubscriptTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Subscript)
    }
  }

  open fun parseSuperscriptTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Superscript)
    }
  }

  open fun parseEmphasizedTag(childTextParts: MutableList<TextPartMut>) {
    for (childTextPart in childTextParts) {
      childTextPart.spans.add(TextPartSpan.Italic)
    }
  }

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

  companion object {
    private const val TAG = "AbstractSitePostParser"

    val LINK_EXTRACTOR = LinkExtractor.builder()
      .linkTypes(EnumSet.of(LinkType.URL))
      .build()
  }

}