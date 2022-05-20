package com.github.k1rakishou.kurobaexlite.helpers.parser

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlNode
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlParser
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.managers.ISiteManager
import com.github.k1rakishou.kurobaexlite.model.SiteIsNotSupported
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import logcat.asLog

class PostCommentParser(
  private val siteManager: ISiteManager
) {
  private val htmlParser = HtmlParser()

  fun parsePostCommentAsText(
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): String? {
    val site = siteManager.bySiteKey(postDescriptor.siteKey)
      ?: throw SiteIsNotSupported(postDescriptor.siteKey)

    val postParser = site.parser()

    return Result.Try {
      val textParts = parsePostComment(postParser, postCommentUnparsed, postDescriptor)
      val totalTextLength = textParts.sumOf { textPart -> textPart.text.length }
      val stringBuilder = StringBuilder(totalTextLength)

      textParts.joinTo(
        buffer = stringBuilder,
        separator = "",
        transform = { textPart -> textPart.text }
      )

      return@Try stringBuilder.toString()
    }.getOrNull()
  }

  fun parsePostComment(
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): List<TextPart> {
    val site = siteManager.bySiteKey(postDescriptor.siteKey)
      ?: throw SiteIsNotSupported(postDescriptor.siteKey)

    val postParser = site.parser()

    return Result
      .Try { parsePostComment(postParser, postCommentUnparsed, postDescriptor) }
      .getOrElse { error ->
        logcatError { "Failed to parse post '${postDescriptor.asReadableString()}, error=${error.asLog()}" }

        val errorMessage = "Failed to parse post '${postDescriptor.asReadableString()}', " +
          "error message: ${error.errorMessageOrClassName()}"

        return@getOrElse listOf(TextPart(text = errorMessage))
      }
  }

  fun parsePostComment(
    postParser: AbstractSitePostParser,
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): List<TextPart> {
    val htmlNodes = htmlParser.parse(postCommentUnparsed).nodes

    return processNodes(postDescriptor, htmlNodes, postParser)
      .map { textPartMut -> postParser.postProcessTextParts(textPartMut) }
      .map { textPartMut -> textPartMut.toTextPartWithSortedSpans() }
  }

  private fun processNodes(
    postDescriptor: PostDescriptor,
    htmlNodes: List<HtmlNode>,
    sitePostParser: AbstractSitePostParser
  ): MutableList<TextPartMut> {
    if (htmlNodes.isEmpty()) {
      return mutableListOf()
    }

    val currentTextParts = mutableListWithCap<TextPartMut>(16)

    for (htmlNode in htmlNodes) {
      when (htmlNode) {
        is HtmlNode.Tag -> {
          val htmlTag = htmlNode.htmlTag
          val childTextParts = processNodes(postDescriptor, htmlTag.children, sitePostParser)
          sitePostParser.parseHtmlNode(htmlTag, childTextParts, postDescriptor)

          currentTextParts.addAll(childTextParts)
        }
        is HtmlNode.Text -> {
          currentTextParts += TextPartMut(htmlNode.text)
        }
      }
    }

    return currentTextParts
  }

}