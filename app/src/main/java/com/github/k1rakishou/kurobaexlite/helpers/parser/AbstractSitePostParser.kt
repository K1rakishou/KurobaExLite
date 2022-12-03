package com.github.k1rakishou.kurobaexlite.helpers.parser

import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlTag
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import logcat.LogPriority
import logcat.logcat
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import java.util.*

abstract class AbstractSitePostParser {

  fun parseHtmlNode(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor
  ) {
    when (htmlTag.tagName) {
      "br" -> parseNewLineTag(childTextParts)
      "p" -> parseParagraphTag(childTextParts)
      "s" -> parseStrikethroughTag(childTextParts)
      "span" -> parseSpanTag(htmlTag, childTextParts, postDescriptor)
      "a" -> parseLinkTag(htmlTag, childTextParts, postDescriptor)
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

  abstract fun parseNewLineTag(childTextParts: MutableList<TextPartMut>)
  abstract fun parseParagraphTag(childTextParts: MutableList<TextPartMut>)
  abstract fun parseStrikethroughTag(childTextParts: MutableList<TextPartMut>)
  abstract fun parseSpanTag(htmlTag: HtmlTag, childTextParts: MutableList<TextPartMut>, postDescriptor: PostDescriptor)
  abstract fun parseLinkTag(htmlTag: HtmlTag, childTextParts: MutableList<TextPartMut>, postDescriptor: PostDescriptor)
  abstract fun parseLinkable(className: String, href: String, postDescriptor: PostDescriptor): TextPartSpan.Linkable?
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