package com.github.k1rakishou.kurobaexlite.helpers.parser

import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlTag
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import java.util.EnumSet
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType

abstract class AbstractSitePostParser {
  protected val LINK_EXTRACTOR = LinkExtractor.builder()
    .linkTypes(EnumSet.of(LinkType.URL))
    .build()

  abstract fun parseHtmlNode(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor
  )

  abstract fun parseLinkable(
    className: String,
    href: String,
    postDescriptor: PostDescriptor
  ): TextPartSpan.Linkable?

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

}