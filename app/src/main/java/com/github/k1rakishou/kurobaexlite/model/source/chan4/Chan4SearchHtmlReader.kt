package com.github.k1rakishou.kurobaexlite.model.source.chan4

import com.github.k1rakishou.kurobaexlite.helpers.extractFileNameExtension
import com.github.k1rakishou.kurobaexlite.helpers.groupOrNull
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlReader
import com.github.k1rakishou.kurobaexlite.helpers.html.SearchException
import com.github.k1rakishou.kurobaexlite.helpers.removeExtensionIfPresent
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class Chan4SearchHtmlReader(
  val catalogDescriptor: CatalogDescriptor,
  val currentOffset: Int
) : HtmlReader<SearchResult> {
  private val postDescriptorRegex by lazy { Pattern.compile("\\/\\/boards\\.4chan(?:nel)?\\.org\\/(\\w+)\\/thread\\/(\\d+)#p(\\d+)") }

  override fun readHtml(
    url: String,
    inputStream: InputStream
  ): SearchResult {
    val htmlDocument = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), url)

    val boardElement = htmlDocument.selectFirst("div[class=board]")
      ?: throw SearchException("Failed to find div[board]")

    val threadElements = boardElement.select("div[class=thread]")
    if (threadElements.isEmpty()) {
      return SearchResult(emptyList())
    }

    val posts = mutableListOf<IPostData>()
    var postOffset = currentOffset

    threadElements.forEach { threadElement ->
      val postContainerElements = threadElement.children()
        .filter { element -> element.attr("class").startsWith("postContainer") }
        .flatMap { it.children() }

      if (postContainerElements.isEmpty()) {
        return@forEach
      }

      postContainerElements.forEach { postElement ->
        val isOp = postElement.hasClass("post op")

        val postUrl = postElement.selectFirst("span[class=postNum desktop]")
          ?.selectFirst("a")
          ?.attr("href")
          ?: return@forEach

        val message = postElement.selectFirst("blockquote[class=postMessage]")?.text() ?: ""
        val subject = postElement.selectFirst("span[class=subject]")?.text() ?: ""

        val postDescriptor = extractPostDescriptor(postUrl)
          ?: return@forEach

        val fileText = postElement.selectFirst("div[class=fileText]")
        val fileThumb = postElement.selectFirst("a[class=fileThumb]")
        val timeMs = postElement.selectFirst("span[class=dateTime]")?.attr("data-utc")?.toLongOrNull()?.times(1000)

        val postImageDataList = parsePostImageData(postDescriptor, fileText, fileThumb)
          ?.let { listOf(it) }
          ?: emptyList()

        posts += if (isOp) {
          OriginalPostData(
            originalPostOrder = postOffset,
            postDescriptor = postDescriptor,
            postSubjectUnparsed = subject,
            postCommentUnparsed = message,
            timeMs = timeMs,
            images = postImageDataList,
            threadRepliesTotal = null,
            threadImagesTotal = null,
            threadPostersTotal = null,
            lastModified = null,
            archived = null,
            closed = null,
            sticky = null,
            bumpLimit = null,
            imageLimit = null,
          )
        } else {
          PostData(
            originalPostOrder = postOffset,
            postDescriptor = postDescriptor,
            postSubjectUnparsed = subject,
            postCommentUnparsed = message,
            timeMs = timeMs,
            images = postImageDataList,
            threadRepliesTotal = null,
            threadImagesTotal = null,
            threadPostersTotal = null,
            lastModified = null,
            archived = null,
            closed = null,
            sticky = null,
            bumpLimit = null,
            imageLimit = null,
          )
        }

        ++postOffset
      }
    }

    return SearchResult(posts)
  }

  private fun parsePostImageData(
    postDescriptor: PostDescriptor,
    fileText: Element?,
    fileThumb: Element?
  ): PostImageData? {
    if (fileText == null || fileThumb == null) {
      return null
    }

    val fileUrl = fileThumb.attr("href")
    if (fileUrl.isEmpty()) {
      return null
    }

    val fullFileUrl = "https:${fileUrl}"
    val extension = fullFileUrl.extractFileNameExtension() ?: "jpg"
    val thumbnailFileUrl = "${fullFileUrl.removeExtensionIfPresent()}s.jpg"

    val fullImageName = fileText.children()
      .firstOrNull { element -> element.hasAttr("target") }
      ?.text()
      ?.trim()

    if (fullImageName.isNullOrEmpty()) {
      return null
    }

    val fileInfoText = (fileText.childNodes().lastOrNull { node -> node is TextNode } as? TextNode)
      ?.wholeText
      ?.takeIf { it.isNotEmpty() }
      ?: return null

    val matcher = FILE_INFO_PATTERN.matcher(fileInfoText)
    if (!matcher.find()) {
      return null
    }

    val fileSize = matcher.groupOrNull(1)?.toFloatOrNull()?.toInt() ?: 0
    val sizeType = matcher.groupOrNull(2) ?: "KB"
    val width = matcher.groupOrNull(3)?.toIntOrNull() ?: 0
    val height = matcher.groupOrNull(4)?.toIntOrNull() ?: 0

    val multiplier = when (sizeType.uppercase(Locale.ENGLISH)) {
      "KB" -> 1000
      "KIB" -> 1024
      "MB" -> 1000 * 1000
      "MIB" -> 1024 * 1024
      "GB" -> 1000 * 1000 * 1000
      "GIB" -> 1024 * 1024 * 1024
      "B" -> 1
      else -> 1024
    }

    val actualFileSize = (fileSize * multiplier)
    val serverFileName = fullFileUrl.substringAfterLast("/")

    val actualThumbnailUrl = thumbnailFileUrl.toHttpUrlOrNull()
      ?: return null
    val actualFullImageUrl = fullFileUrl.toHttpUrlOrNull()
      ?: return null

    return PostImageData(
      thumbnailUrl = actualThumbnailUrl,
      fullImageUrl = actualFullImageUrl,
      originalFileNameEscaped = fullImageName,
      serverFileName = serverFileName,
      ext = extension,
      width = width,
      height = height,
      fileSize = actualFileSize,
      ownerPostDescriptor = postDescriptor
    )
  }

  private fun extractPostDescriptor(postUrl: String): PostDescriptor? {
    val matcher = postDescriptorRegex.matcher(postUrl)
    if (!matcher.find()) {
      return null
    }

    val boardCode = matcher.groupOrNull(1) ?: return null
    val threadNo = matcher.groupOrNull(2)?.toLongOrNull()?.takeIf { it > 0L } ?: return null
    val postNo = matcher.groupOrNull(3)?.toLongOrNull()?.takeIf { it > 0L } ?: return null

    return PostDescriptor.create(
      siteKey = catalogDescriptor.siteKey,
      boardCode = boardCode,
      threadNo = threadNo,
      postNo = postNo
    )
  }

  companion object {
    private val FILE_INFO_PATTERN = Pattern.compile("(\\d*\\.*\\d+)\\s*([G?|M?|K?i?B]+),\\s*(\\d+)x(\\d+)")
  }

}