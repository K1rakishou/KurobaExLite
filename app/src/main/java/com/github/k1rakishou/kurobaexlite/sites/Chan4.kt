package com.github.k1rakishou.kurobaexlite.sites

import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey

class Chan4 : Site {
  private val chan4CatalogInfo by lazy { CatalogInfo() }
  private val chan4ThreadInfo by lazy { ThreadInfo() }
  private val chan4PostImageInfo by lazy { PostImageInfo() }

  override val siteKey: SiteKey = SITE_KEY
  override val readableName: String = "4chan"

  override fun catalogInfo(): Site.CatalogInfo = chan4CatalogInfo
  override fun threadInfo(): Site.ThreadInfo? = chan4ThreadInfo
  override fun postImageInfo(): Site.PostImageInfo = chan4PostImageInfo

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