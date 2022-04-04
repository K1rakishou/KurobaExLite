package com.github.k1rakishou.kurobaexlite.ui.screens.media

import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import okhttp3.HttpUrl

sealed class MediaViewerParams {
  val initialImage: HttpUrl
    get() {
      return when (this) {
        is Catalog -> this.initialImageUrl
        is Images -> this.initialImageUrl
        is Thread -> this.initialImageUrl
      }
    }

  data class Catalog(val catalogDescriptor: CatalogDescriptor, val initialImageUrl: HttpUrl): MediaViewerParams()
  data class Thread(val threadDescriptor: ThreadDescriptor, val initialImageUrl: HttpUrl) : MediaViewerParams()
  data class Images(val images: List<PostCellImageData>, val initialImageUrl: HttpUrl) : MediaViewerParams()
}