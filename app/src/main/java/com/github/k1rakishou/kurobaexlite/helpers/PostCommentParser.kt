package com.github.k1rakishou.kurobaexlite.helpers

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlNode
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlParser
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanThemeColorId
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class PostCommentParser {
  private val htmlParser = HtmlParser()

  private val dispatcher = Executors.newFixedThreadPool(
    Runtime
      .getRuntime()
      .availableProcessors()
      .coerceAtLeast(4)
  ).asCoroutineDispatcher()

  suspend fun parsePostComment(postData: PostData): List<TextPart> {
    return withContext(dispatcher) {
      return@withContext Result
        .Try { parsePostCommentInternal(postData) }
        .getOrElse { error ->
          val errorMessage = "Failed to parse post '${postData.postDescriptor}', " +
            "error: ${error.errorMessageOrClassName()}"

          return@getOrElse listOf(TextPart(text = errorMessage))
        }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun parsePostCommentInternal(postData: PostData): List<TextPart> {
    val htmlNodes = htmlParser.parse(postData.postCommentUnparsed).nodes
    return processNodes(postData.postDescriptor, htmlNodes)
  }

  private fun processNodes(postDescriptor: PostDescriptor, htmlNodes: List<HtmlNode>): MutableList<TextPart> {
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

          when (htmlTag.tagName) {
            "br" -> {
              childTextParts += TextPart(text = "\n")
            }
            "span" -> {
              if (htmlTag.hasClass("quote")) {
                for (childTextPart in childTextParts) {
                  childTextPart.spans.add(TextPartSpan.FgColorId(ChanThemeColorId.PostInlineQuote))
                }
              }
            }
            "wbr" -> {
              // no-op
            }
            "s" -> {
              for (childTextPart in childTextParts) {
                childTextPart.spans.add(TextPartSpan.Spoiler)
              }
            }
            else -> {
              // TODO(KurobaEx):
//              logcatError { "Unsupported tag with name '${htmlTag.tagName}' found. (postDescriptor=$postDescriptor, htmlTag=$htmlTag)" }
            }
          }

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

  data class TextPart(
    val text: String,
    val spans: MutableList<TextPartSpan> = mutableListOf()
  )

  sealed class TextPartSpan {
    class BgColor(val color: Int) : TextPartSpan()
    class FgColor(val color: Int) : TextPartSpan()
    class BgColorId(val colorId: ChanThemeColorId) : TextPartSpan()
    class FgColorId(val colorId: ChanThemeColorId) : TextPartSpan()
    object Spoiler : TextPartSpan()
  }

}