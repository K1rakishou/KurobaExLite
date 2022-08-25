package com.github.k1rakishou.kurobaexlite.features.posts.search.image

import androidx.annotation.DrawableRes
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.RemoteImageSearchSettings
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

abstract class ImageSearchInstance(
  protected val remoteImageSearchSettings: RemoteImageSearchSettings,
  val type: ImageSearchInstanceType,
  val baseUrl: HttpUrl,
  @DrawableRes val icon: Int
) {

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  private var _currentPage = 0
  val currentPage: Int
    get() = _currentPage

  private var _searchQuery: String? = null
  val searchQuery: String?
    get() = _searchQuery

  abstract val cookies: String?

  abstract suspend fun init()
  abstract fun buildSearchUrl(query: String, page: Int?): HttpUrl
  abstract suspend fun updateCookies(newCookies: String)

  fun updateLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    _rememberedFirstVisibleItemIndex = firstVisibleItemIndex
    _rememberedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
  }

  fun updateCurrentPage(page: Int) {
    _currentPage = page
  }

  fun updateSearchQuery(newQuery: String) {
    _searchQuery = newQuery
  }

  companion object {
    fun createAll(remoteImageSearchSettings: RemoteImageSearchSettings): List<ImageSearchInstance> {
      return listOf(
        YandexInstance(remoteImageSearchSettings)
      )
    }
  }
}

class YandexInstance(
  remoteImageSearchSettings: RemoteImageSearchSettings
) : ImageSearchInstance(
  remoteImageSearchSettings = remoteImageSearchSettings,
  type = ImageSearchInstanceType.Yandex,
  baseUrl = "https://yandex.com".toHttpUrl(),
  icon = R.drawable.yandex_favicon
) {

  private var _cookies: String? = null
  override val cookies: String?
    get() = _cookies


  override suspend fun init() {
    _cookies = remoteImageSearchSettings.yandexImageSearchCookies.read()
      .takeIf { cookiesString -> cookiesString.isNotEmpty() }
  }

  override suspend fun updateCookies(newCookies: String) {
    _cookies = newCookies
    remoteImageSearchSettings.yandexImageSearchCookies.write(newCookies)
  }

  override fun buildSearchUrl(query: String, page: Int?): HttpUrl {
    return with(baseUrl.newBuilder()) {
      addPathSegment("images")
      addPathSegment("search")
      addQueryParameter("text", query)

      if (page != null) {
        addQueryParameter("p", "${page}")
      }

      build()
    }

  }

}

enum class ImageSearchInstanceType {
  Yandex
}