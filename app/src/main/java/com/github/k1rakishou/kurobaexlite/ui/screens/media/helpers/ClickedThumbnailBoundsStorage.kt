package com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers

import androidx.compose.ui.geometry.Rect
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage

class ClickedThumbnailBoundsStorage {
  private var clickedThumbnailBounds: ClickedThumbnailBounds? = null

  fun clear() {
    clickedThumbnailBounds = null
  }

  fun storeBounds(postImage: IPostImage, bounds: Rect) {
    clickedThumbnailBounds = ClickedThumbnailBounds(postImage, bounds)
  }

  fun getAndConsume(): ClickedThumbnailBounds? {
    val copy = clickedThumbnailBounds?.copy()
    clickedThumbnailBounds = null

    return copy
  }

  data class ClickedThumbnailBounds(
    val postImage: IPostImage,
    val bounds: Rect
  )

}