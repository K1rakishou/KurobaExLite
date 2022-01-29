package com.github.k1rakishou.kurobaexlite.sites

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import okhttp3.HttpUrl

class Chan4 : Site {
  private val chan4CatalogInfo by lazy { CatalogInfo() }
  private val chan4ThreadInfo by lazy { ThreadInfo() }
  private val chan4PostImageInfo by lazy { PostImageInfo() }

  override val siteKey: SiteKey = SITE_KEY
  override val readableName: String = "4chan"

  override fun catalogInfo(): Site.CatalogInfo = chan4CatalogInfo
  override fun threadInfo(): Site.ThreadInfo? = chan4ThreadInfo
  override fun postImageInfo(): Site.PostImageInfo = chan4PostImageInfo

  override fun resolveDescriptorFromUrl(url: HttpUrl): ResolvedDescriptor? {
    val parts = url.pathSegments
    if (parts.isEmpty()) {
      return null
    }

    val boardCode = parts[0]

    if (parts.size < 3) {
      // Board mode
      return ResolvedDescriptor.CatalogOrThread(CatalogDescriptor(siteKey, boardCode))
    }

    // Thread mode
    val threadNo = (parts[2].toIntOrNull() ?: -1).toLong()
    var postId = -1L
    val fragment = url.fragment

    if (fragment != null) {
      val index = fragment.indexOf("p")
      if (index >= 0) {
        postId = (fragment.substring(index + 1).toIntOrNull() ?: -1).toLong()
      }
    }

    if (threadNo < 0L) {
      return null
    }

    val threadDescriptor = ThreadDescriptor.create(
      siteKey = siteKey,
      boardCode = boardCode,
      threadNo = threadNo
    )

    if (postId <= 0) {
      return ResolvedDescriptor.CatalogOrThread(threadDescriptor)
    }

    val postDescriptor = PostDescriptor(threadDescriptor, postId)
    return ResolvedDescriptor.Post(postDescriptor)
  }

  class CatalogInfo : Site.CatalogInfo {
    override fun catalogUrl(boardCode: String): String {
      return "https://a.4cdn.org/${boardCode}/catalog.json"
    }
  }

  class ThreadInfo : Site.ThreadInfo {
    override fun threadUrl(boardCode: String, threadNo: Long): String {
      return "https://a.4cdn.org/${boardCode}/thread/${threadNo}.json"
    }
  }

  class PostImageInfo : Site.PostImageInfo {
    override fun thumbnailUrl(boardCode: String, tim: Long, extension: String): String {
      return "https://i.4cdn.org/${boardCode}/${tim}s.${extension}"
    }
  }

  companion object {
    val SITE_KEY = SiteKey("4chan")
  }

}