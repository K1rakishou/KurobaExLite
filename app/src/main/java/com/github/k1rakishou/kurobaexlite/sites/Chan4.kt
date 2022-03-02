package com.github.k1rakishou.kurobaexlite.sites

import com.github.k1rakishou.kurobaexlite.model.data.local.BoardsData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import okhttp3.HttpUrl
import org.koin.java.KoinJavaComponent.inject

class Chan4 : Site {
  private val chan4DataSource by inject<Chan4DataSource>(Chan4DataSource::class.java)

  private val chan4CatalogInfo by lazy { CatalogInfo(chan4DataSource) }
  private val chan4ThreadInfo by lazy { ThreadInfo(chan4DataSource) }
  private val chan4BoardsInfo by lazy { BoardsInfo(chan4DataSource) }
  private val chan4PostImageInfo by lazy { PostImageInfo() }

  override val siteKey: SiteKey = SITE_KEY
  override val readableName: String = "4chan"

  override fun catalogInfo(): Site.CatalogInfo = chan4CatalogInfo
  override fun threadInfo(): Site.ThreadInfo = chan4ThreadInfo
  override fun boardsInfo(): Site.BoardsInfo = chan4BoardsInfo
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

  class CatalogInfo(private val chan4DataSource: Chan4DataSource) : Site.CatalogInfo {

    override fun catalogUrl(boardCode: String): String {
      return "https://a.4cdn.org/${boardCode}/catalog.json"
    }

    override fun catalogDataSource(): ICatalogDataSource<CatalogDescriptor, CatalogData> {
      return chan4DataSource
    }

  }

  class ThreadInfo(private val chan4DataSource: Chan4DataSource) : Site.ThreadInfo {

    override fun threadUrl(boardCode: String, threadNo: Long): String {
      return "https://a.4cdn.org/${boardCode}/thread/${threadNo}.json"
    }

    override fun threadDataSource(): IThreadDataSource<ThreadDescriptor, ThreadData> {
      return chan4DataSource
    }

  }

  class BoardsInfo(private val chan4DataSource: Chan4DataSource) : Site.BoardsInfo {

    override fun boardsUrl(): String {
      return "https://a.4cdn.org/boards.json"
    }

    override fun siteBoardsDataSource(): IBoardDataSource<SiteKey, BoardsData> {
      return chan4DataSource
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