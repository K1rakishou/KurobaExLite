package com.github.k1rakishou.kurobaexlite.helpers

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlNode
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlParser
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlTag
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanThemeColorId
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class PostCommentParser {
  private val htmlParser = HtmlParser()

  private val dispatcher = Executors.newFixedThreadPool(
    Runtime
      .getRuntime()
      .availableProcessors()
      .coerceAtLeast(4)
  ).asCoroutineDispatcher()

  suspend fun parsePostComment(
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): List<TextPart> {
    return withContext(dispatcher) {
      return@withContext Result
        .Try { parsePostCommentInternal(postCommentUnparsed, postDescriptor) }
        .getOrElse { error ->
          val errorMessage = "Failed to parse post '${postDescriptor}', " +
            "error: ${error.errorMessageOrClassName()}"

          return@getOrElse listOf(TextPart(text = errorMessage))
        }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun parsePostCommentInternal(
    postCommentUnparsed: String,
    postDescriptor: PostDescriptor
  ): List<TextPart> {
    val htmlNodes = htmlParser.parse(postCommentUnparsed).nodes
    return processNodes(postDescriptor, htmlNodes)
  }

  private fun processNodes(
    postDescriptor: PostDescriptor,
    htmlNodes: List<HtmlNode>
  ): MutableList<TextPart> {
    if (htmlNodes.isEmpty()) {
      return mutableListOf()
    }

    val currentTextPartsLazy = lazy(mode = LazyThreadSafetyMode.NONE) { mutableListWithCap<TextPart>(16) }
    val currentTextParts by currentTextPartsLazy

    for (htmlNode in htmlNodes) {
      when (htmlNode) {
        is HtmlNode.Tag -> {
          val htmlTag = htmlNode.htmlTag
          val childTextParts = processNodes(postDescriptor, htmlTag.children)
          parseHtmlNode(htmlTag, childTextParts, postDescriptor)

          currentTextParts.addAll(childTextParts)
        }
        is HtmlNode.Text -> {
          currentTextParts += TextPart(htmlNode.text)
        }
      }
    }

    if (currentTextPartsLazy.isInitialized()) {
      return currentTextParts
    }

    return mutableListOf()
  }

  private fun parseHtmlNode(
    htmlTag: HtmlTag,
    childTextParts: MutableList<TextPart>,
    postDescriptor: PostDescriptor
  ) {
    when (htmlTag.tagName) {
      "br" -> {
        childTextParts += TextPart(text = "\n")
      }
      "s" -> {
        for (childTextPart in childTextParts) {
          childTextPart.spans.add(TextPartSpan.Spoiler)
        }
      }
      "span",
      "a" -> {
        if (htmlTag.hasClass("quote")) {
          for (childTextPart in childTextParts) {
            childTextPart.spans.add(TextPartSpan.FgColorId(ChanThemeColorId.PostInlineQuote))
          }

          return
        }

        val className = htmlTag.classAttrOrNull()
          ?: return

        val childTextPart = if (childTextParts.size == 1) {
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

        childTextPart.spans += linkable
      }
      "wbr" -> {
        // no-op
      }
      else -> {
        logcatError {
          "Unsupported tag with name '${htmlTag.tagName}' found. " +
            "(postDescriptor=$postDescriptor, htmlTag=$htmlTag)"
        }
      }
    }
  }

  private fun mergeChildTextPartsIntoOne(
    childTextParts: List<TextPart>
  ): TextPart {
    val totalText = StringBuilder(childTextParts.sumOf { it.text.length })
    val totalSpans = mutableListWithCap<TextPartSpan>(childTextParts.sumOf { it.spans.size })

    for (inputChildTextPart in childTextParts) {
      totalText.append(inputChildTextPart.text)
      totalSpans.addAll(inputChildTextPart.spans)
    }

    return TextPart(
      text = totalText.toString(),
      spans = totalSpans
    )
  }

  @VisibleForTesting
  fun parseLinkable(
    className: String,
    href: String,
    postDescriptor: PostDescriptor
  ): TextPartSpan.Linkable? {
    val isDeadLink = className.equals(other = "deadlink", ignoreCase = true)
    val isQuoteLink = className.equals(other = "quotelink", ignoreCase = true)

    if (!isDeadLink && !isQuoteLink) {
      return null
    }

    if (href.startsWith("#") || href.startsWith(">>")) {
      // Internal quote, e.g. '#p370525473'
      // or
      // Internal dead quote, e.g. '>>370525473'
      val postNo = href.drop(2).toLongOrNull()
        ?: return null

      if (postNo <= 0) {
        return null
      }

      val quotePostDescriptor = PostDescriptor(
        threadDescriptor = postDescriptor.threadDescriptor,
        postNo = postNo
      )

      return TextPartSpan.Linkable.Quote(
        crossThread = false,
        dead = isDeadLink,
        postDescriptor = quotePostDescriptor
      )
    }

    if (href.startsWith("/")) {
      // External quote

      val hrefPreprocessed = preprocessHref(href)

      when {
        href.contains("/thread/") -> {
          // Thread quotes:
          // '/qst/thread/5126311#p5126311'
          // '/aco/thread/6149612#p6149612'

          // Cross-thread quotes:
          // '/vg/thread/369649921#p369650787'
          // '/vg/thread/369649921'

          val fullPathSplit = hrefPreprocessed.split("/")
            .filter { part -> part.isNotBlank() }

          val threadSegment = fullPathSplit.getOrNull(1)
          if (threadSegment?.equals("thread", ignoreCase = true) != true) {
            return null
          }

          val boardCode = fullPathSplit.getOrNull(0)
            ?: return null
          val threadNoPostNo = fullPathSplit.getOrNull(2)
            ?: return null

          var threadNo = 0L
          var postNo = 0L

          if (threadNoPostNo.contains("#p")) {
            val threadNoPostNoSplit = threadNoPostNo.split("#p")

            threadNo = threadNoPostNoSplit.getOrNull(0)?.toLongOrNull()
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
            crossThread = true,
            dead = isDeadLink,
            postDescriptor = quotePostDescriptor
          )
        }
        hrefPreprocessed.contains("#s") -> {
          // Search quotes:
          // '/vg/catalog#s=tesog%2F'
          // '/aco/catalog#s=weg%2F'

          val fullPathSplit = hrefPreprocessed.split("/")
            .filter { part -> part.isNotBlank() }

          val boardCode = fullPathSplit.getOrNull(0)
            ?: return null
          val theRest = fullPathSplit.getOrNull(1)?.removePrefix("catalog#s=")
            ?: return null

          val decodedQuery = decodeUrlOrNull(theRest, StandardCharsets.UTF_8.name())
            ?.removeSuffix("/")
            ?: return null

          return TextPartSpan.Linkable.Search(
            boardCode = boardCode,
            searchQuery = decodedQuery
          )
        }
        else -> {
          val fullPathSplit = hrefPreprocessed.split("/")
            .filter { part -> part.isNotBlank() }

          if (fullPathSplit.size == 1) {
            // Board quotes:
            // '/jp/'
            // '/jp/'
            val boardCode = fullPathSplit.get(0).removePrefix("/").removeSuffix("/").trim()
            if (boardCode.isBlank()) {
              return null
            }

            return TextPartSpan.Linkable.Board(
              boardCode = boardCode
            )
          }
        }
      }
    }

    return null
  }

  private fun preprocessHref(href: String): String {
    // //boards.4channel.org/qst/thread/5126311#p5126311  -> qst/thread/5126311#p5126311
    // /qst/thread/5126311#p5126311                       -> qst/thread/5126311#p5126311

    var resultHref = href

    if (href.startsWith("//")) {
      resultHref = href.removePrefix("//").dropWhile { it != '/' }
    }

    return resultHref.removePrefix("/")
  }

  data class TextPart(
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

  }

  sealed class TextPartSpan {

    class BgColor(val color: Int) : TextPartSpan()
    class FgColor(val color: Int) : TextPartSpan()
    class BgColorId(val colorId: ChanThemeColorId) : TextPartSpan()
    class FgColorId(val colorId: ChanThemeColorId) : TextPartSpan()
    object Spoiler : TextPartSpan()

    sealed class Linkable : TextPartSpan() {

      fun serialize(): Buffer {
        val buffer = Buffer()

        buffer.writeInt(id().value)
        serializeLinkable(buffer)

        return buffer
      }

      private fun id(): Id {
        return when (this) {
          is Board -> Id.Board
          is Quote -> Id.Quote
          is Search -> Id.Search
          is Url -> Id.Url
        }
      }

      protected abstract fun serializeLinkable(buffer: Buffer)

      data class Quote(
        val crossThread: Boolean,
        val dead: Boolean,
        val postDescriptor: PostDescriptor
      ) : Linkable() {
        override fun serializeLinkable(buffer: Buffer) {
          buffer.writeByte(if (crossThread) 1 else 0)
          buffer.writeByte(if (dead) 1 else 0)
          postDescriptor.serialize(buffer)
        }

        companion object {
          fun deserializeLinkable(buffer: Buffer): Quote {
            val crossThread = buffer.readByte() == 1.toByte()
            val dead = buffer.readByte() == 1.toByte()
            val postDescriptor = PostDescriptor.deserialize(buffer)

            return Quote(crossThread, dead, postDescriptor)
          }
        }
      }

      data class Search(
        val boardCode: String,
        val searchQuery: String
      ) : Linkable() {
        override fun serializeLinkable(buffer: Buffer) {
          buffer.writeUtfString(boardCode)
          buffer.writeUtfString(searchQuery)
        }

        companion object {
          fun deserializeLinkable(buffer: Buffer): Search {
            val boardCode = buffer.readUtfString()
            val searchQuery = buffer.readUtfString()

            return Search(boardCode, searchQuery)
          }
        }
      }

      data class Board(
        val boardCode: String
      ) : Linkable() {
        override fun serializeLinkable(buffer: Buffer) {
          buffer.writeUtfString(boardCode)
        }

        companion object {
          fun deserializeLinkable(buffer: Buffer): Board {
            val boardCode = buffer.readUtfString()
            return Board(boardCode)
          }
        }
      }

      data class Url(
        val url: String
      ) : Linkable() {
        override fun serializeLinkable(buffer: Buffer) {
          buffer.writeUtfString(url)
        }

        companion object {
          fun deserializeLinkable(buffer: Buffer): Url {
            val url = buffer.readUtfString()
            return Url(url)
          }
        }
      }

      enum class Id(val value: Int) {
        Quote(0),
        Search(1),
        Board(2),
        Url(3);

        companion object {
          fun fromValue(value: Int): Id? {
            return when (value) {
              0 -> Quote
              1 -> Search
              2 -> Board
              3 -> Url
              else -> null
            }
          }
        }
      }

      companion object {
        fun deserialize(buffer: Buffer): Linkable? {
          val idValue = buffer.readInt()
          val id = Id.fromValue(idValue)
          if (id == null) {
            logcatError(tag = "Linkable.deserialize()") { "Unknown id: ${idValue}" }
            return null
          }

          return when (id) {
            Id.Quote -> Quote.deserializeLinkable(buffer)
            Id.Search -> Search.deserializeLinkable(buffer)
            Id.Board -> Board.deserializeLinkable(buffer)
            Id.Url -> Url.deserializeLinkable(buffer)
          }
        }
      }

    }

  }

}