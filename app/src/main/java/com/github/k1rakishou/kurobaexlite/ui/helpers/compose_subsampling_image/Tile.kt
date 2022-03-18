package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import android.graphics.Bitmap

internal class Tile {
  @get:Synchronized
  @set:Synchronized
  var bitmap: Bitmap? = null
  @get:Synchronized
  @set:Synchronized
  var sampleSize = 0
  @get:Synchronized
  @set:Synchronized
  var loading = false
  @get:Synchronized
  @set:Synchronized
  var visible = false
  @get:Synchronized
  @set:Synchronized
  var error = false

  // sRect
  @get:Synchronized
  val sourceRect: RectMut = RectMut(0, 0, 0, 0)

  // vRect
  @get:Synchronized
  val screenRect: RectMut = RectMut(0, 0, 0, 0)

  // fileSRect
  @get:Synchronized
  val fileSourceRect: RectMut = RectMut(0, 0, 0, 0)

  @Synchronized
  fun recycle() {
    bitmap?.recycle()
    bitmap = null
  }

  override fun toString(): String {
    return "Tile(sampleSize=$sampleSize, loading=$loading, visible=$visible, " +
      "error=$error, sourceRect=$sourceRect, screenRect=$screenRect, fileSourceRect=$fileSourceRect)"
  }

}