package com.github.k1rakishou.kurobaexlite.features.media

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import java.io.File

@Immutable
sealed class ImageLoadState {
  abstract val postImage: IPostImage

  val fullImageUrl by lazy { postImage.fullImageAsUrl }
  val fullImageUrlAsString by lazy { postImage.fullImageAsString }

  data class PreparingForLoading(
    override val postImage: IPostImage
  ) : ImageLoadState()

  data class Progress(
    val progress: Float,
    override val postImage: IPostImage
  ) : ImageLoadState()

  data class Error(
    override val postImage: IPostImage,
    val exception: Throwable
  ) : ImageLoadState()

  data class Ready(
    override val postImage: IPostImage,
    val imageFile: File?
  ) : ImageLoadState()
}