package com.github.k1rakishou.kurobaexlite.ui.screens.media

import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import java.io.File
import okhttp3.HttpUrl

sealed class ImageLoadState {
  val fullImageUrl: HttpUrl
    get() {
      return when (this) {
        is Loading -> postImageData.fullImageUrl
        is Error -> postImageData.fullImageUrl
        is Ready -> postImageData.fullImageUrl
      }
    }

  val fullImageUrlAsString: String by lazy { fullImageUrl.toString() }

  data class Loading(val postImageData: PostImageData) : ImageLoadState()

  data class Error(
    val postImageData: PostImageData,
    val exception: Throwable
  ) : ImageLoadState()

  data class Ready(
    val postImageData: PostImageData,
    val imageFile: File
  ) : ImageLoadState()
}