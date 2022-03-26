package com.github.k1rakishou.kurobaexlite.ui.screens.media

import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import java.io.File
import okhttp3.HttpUrl

sealed class ImageLoadState {
  abstract val postImageData: PostImageData

  val fullImageUrl: HttpUrl
    get() = postImageData.fullImageUrl

  val fullImageUrlAsString: String by lazy { fullImageUrl.toString() }

  data class Loading(
    override val postImageData: PostImageData
    ) : ImageLoadState()

  data class Error(
    override val postImageData: PostImageData,
    val exception: Throwable
  ) : ImageLoadState()

  data class Ready(
    override val postImageData: PostImageData,
    val imageFile: File
  ) : ImageLoadState()
}