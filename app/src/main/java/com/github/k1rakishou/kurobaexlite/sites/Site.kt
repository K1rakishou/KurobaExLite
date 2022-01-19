package com.github.k1rakishou.kurobaexlite.sites

import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey

interface Site {
  val siteKey: SiteKey
  val readableName: String

  fun catalogInfo(): CatalogInfo?
  fun threadInfo(): ThreadInfo?
  fun postImageInfo(): PostImageInfo?

  interface CatalogInfo  {
    fun catalogUrl(boardCode: String): String
  }

  interface ThreadInfo  {
    fun threadUrl(boardCode: String, threadNo: Long): String
  }

  interface PostImageInfo {
    fun thumbnailUrl(boardCode: String, tim: Long, extension: String): String
  }
}