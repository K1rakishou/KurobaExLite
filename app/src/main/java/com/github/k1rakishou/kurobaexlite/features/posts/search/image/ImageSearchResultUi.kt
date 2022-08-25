package com.github.k1rakishou.kurobaexlite.features.posts.search.image

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.interactors.image_search.YandexImageSearch
import okhttp3.HttpUrl

@Immutable
class ImageSearchResultUi(
  val thumbnailUrl: HttpUrl,
  val fullImageUrls: List<HttpUrl>,
  val sizeInByte: Long? = null,
  val width: Int? = null,
  val height: Int? = null,
  val extension: String? = null
) {

  fun hasImageInfo(): Boolean {
    return sizeInByte != null || width != null || height != null || extension != null
  }

  companion object {
    fun fromImageSearchResult(imageSearchResult: YandexImageSearch.ImageSearchResult): ImageSearchResultUi {
      return ImageSearchResultUi(
        thumbnailUrl = imageSearchResult.thumbnailUrl,
        fullImageUrls = imageSearchResult.fullImageUrls,
        sizeInByte = imageSearchResult.sizeInByte,
        width = imageSearchResult.width,
        height = imageSearchResult.height,
        extension = imageSearchResult.extension,
      )
    }
  }
}