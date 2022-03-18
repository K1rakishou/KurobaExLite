package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

internal data class RectMut(
  var left: Int,
  var top: Int,
  var right: Int,
  var bottom: Int
) {
  var topLeft: Offset = Offset.Zero
    private set
  var size: Size = Size.Zero
    private set

  init {
    onUpdated()
  }

  fun set(left: Int, top: Int, right: Int, bottom: Int) {
    this.left = left
    this.top = top
    this.right = right
    this.bottom = bottom

    onUpdated()
  }

  fun set(other: RectMut) {
    left = other.left
    top = other.top
    right = other.right
    bottom = other.bottom

    onUpdated()
  }

  fun toRect(): Rect {
    return Rect(Offset(left.toFloat(), top.toFloat()), Offset(right.toFloat(), bottom.toFloat()))
  }

  fun toAndroidRect(): android.graphics.Rect {
    return android.graphics.Rect(left, top, right, bottom)
  }

  private fun onUpdated() {
    topLeft = Offset(left.toFloat(), top.toFloat())
    size = Size((right - left).toFloat(), (bottom - top).toFloat())
  }

}