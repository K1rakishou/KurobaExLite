package com.github.k1rakishou.kurobaexlite.ui.screens.media

import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import java.io.File

sealed class ImageLoadState {
  abstract val postImageData: IPostImage

  val fullImageUrl by lazy { postImageData.fullImageAsUrl }
  val fullImageUrlAsString by lazy { postImageData.fullImageAsString }

  data class Loading(
    override val postImageData: IPostImage
    ) : ImageLoadState()

  data class Error(
    override val postImageData: IPostImage,
    val exception: Throwable
  ) : ImageLoadState()

  data class Ready(
    override val postImageData: IPostImage,
    val imageFile: File
  ) : ImageLoadState()
}