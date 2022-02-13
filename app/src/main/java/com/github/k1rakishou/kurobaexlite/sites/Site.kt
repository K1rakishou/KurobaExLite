package com.github.k1rakishou.kurobaexlite.sites

import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import okhttp3.HttpUrl

interface Site {
  val siteKey: SiteKey
  val readableName: String

  fun catalogInfo(): CatalogInfo?
  fun threadInfo(): ThreadInfo?
  fun postImageInfo(): PostImageInfo?
  fun resolveDescriptorFromUrl(url: HttpUrl): ResolvedDescriptor?

  interface CatalogInfo  {
    fun catalogUrl(boardCode: String): String
    fun catalogDataSource(): ICatalogDataSource<CatalogDescriptor, CatalogData>
  }

  interface ThreadInfo  {
    fun threadUrl(boardCode: String, threadNo: Long): String
    fun threadDataSource(): IThreadDataSource<ThreadDescriptor, ThreadData>
  }

  interface PostImageInfo {
    fun thumbnailUrl(boardCode: String, tim: Long, extension: String): String
  }
}