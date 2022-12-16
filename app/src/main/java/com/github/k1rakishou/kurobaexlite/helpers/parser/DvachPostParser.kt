package com.github.k1rakishou.kurobaexlite.helpers.parser

import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlTag
import com.github.k1rakishou.kurobaexlite.helpers.html.StaticHtmlColorRepository
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanThemeColorId

class DvachPostParser(
  staticHtmlColorRepository: StaticHtmlColorRepository
) : Chan4PostParser(staticHtmlColorRepository) {

  override fun parseSpanTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor
  ) {
    super.parseSpanTag(htmlTag, childTextParts, postDescriptor)

    if (htmlTag.hasClass("unkfunc")) {
      for (childTextPart in childTextParts) {
        childTextPart.spans.add(TextPartSpan.FgColorId(ChanThemeColorId.PostInlineQuote))
      }
    }

    if (htmlTag.hasClass("post__pomyanem")) {
      for (childTextPart in childTextParts) {
        childTextPart.spans.add(TextPartSpan.FgColorId(ChanThemeColorId.Accent))
      }
    }

    parseLinkTag(htmlTag, childTextParts, postDescriptor)
  }

  override fun parseLinkTag(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPartMut>,
    postDescriptor: PostDescriptor
  ) {
    val className = htmlTag.classAttrOrNull()

    var childTextPart = if (childTextParts.size == 1) {
      childTextParts.first()
    } else {
      val mergedTextPart = mergeChildTextPartsIntoOne(childTextParts)

      childTextParts.clear()
      childTextParts.add(mergedTextPart)

      mergedTextPart
    }

    val isDeadLink = htmlTag.tagName == "span" && htmlTag.hasClass("deadlink")

    val href = if (isDeadLink) {
      childTextPart.text
    } else {
      htmlTag.attrUnescapedOrNull("href")
    }

    if (href.isNullOrEmpty()) {
      return
    }

    val linkable = parseLinkable(className, href, postDescriptor)
    if (linkable == null) {
      logcatError { "Failed to parse linkable. className='$className', href='$href'" }
      return
    }

    if (linkable is TextPartSpan.Linkable.Quote) {
      childTextPart = quoteTrimUnnecessaryCharacters(childTextPart)

      childTextParts.clear()
      childTextParts.add(childTextPart)
    }

    childTextPart.spans += linkable
  }

  override fun parseParagraphTag(childTextParts: MutableList<TextPartMut>) {
    childTextParts += TextPartMut(text = "\n")
    childTextParts += TextPartMut(text = "\n")
  }

  override fun parseLinkable(className: String?, href: String, postDescriptor: PostDescriptor): TextPartSpan.Linkable? {
    if (className == null) {
      return TextPartSpan.Linkable.Url(href)
    }

    val isQuoteLink = className.equals(other = "post-reply-link", ignoreCase = true)
    if (!isQuoteLink) {
      return null
    }

    val hrefPreprocessed = preprocessHref(href)

    // '/a/res/7526735.html#7526735'
    // '/a/res/7526735.html'

    val fullPathSplit = hrefPreprocessed.split("/")
      .filter { part -> part.isNotBlank() }

    val resSegment = fullPathSplit.getOrNull(1)
    if (resSegment?.equals("res", ignoreCase = true) != true) {
      return null
    }

    val boardCode = fullPathSplit.getOrNull(0)
      ?: return null
    val threadNoPostNo = fullPathSplit.getOrNull(2)
      ?: return null

    var threadNo = 0L
    var postNo = 0L

    if (threadNoPostNo.contains("#")) {
      val threadNoPostNoSplit = threadNoPostNo.split("#")

      threadNo = threadNoPostNoSplit.getOrNull(0)?.replace(".html", "")?.toLongOrNull()
        ?: return null
      postNo = threadNoPostNoSplit.getOrNull(1)?.toLongOrNull()
        ?: return null
    } else {
      threadNo = threadNoPostNo.toLongOrNull()
        ?: return null
      postNo = threadNo
    }

    if (threadNo <= 0 || postNo <= 0) {
      return null
    }

    val quotePostDescriptor = PostDescriptor.create(
      siteKey = postDescriptor.siteKey,
      boardCode = boardCode,
      threadNo = threadNo,
      postNo = postNo
    )

    return TextPartSpan.Linkable.Quote(
      crossThread = threadNo != postDescriptor.threadNo,
      dead = false,
      postDescriptor = quotePostDescriptor
    )
  }

  // >>7484866 (OP) ->  >>7484866
  // >>7499275 →    ->  >>7499275
  private fun quoteTrimUnnecessaryCharacters(childTextPart: TextPartMut): TextPartMut {
    val newQuoteText = buildString {
      var offset = 0
      var processingPostNumber = false
      val text = childTextPart.text

      while (offset < text.length) {
        val ch = text.getOrNull(offset) ?: break

        if (ch.isDigit()) {
          processingPostNumber = true
        }

        if (ch.isWhitespace() && !processingPostNumber) {
          continue
        }

        if (ch != '>' && !ch.isDigit()) {
          break
        }

        append(text[offset])
        ++offset
      }
    }

    if (newQuoteText.isBlank()) {
      return childTextPart
    }

    return childTextPart.copy(text = newQuoteText)
  }

}