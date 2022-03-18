package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import android.graphics.PointF

internal class ScaleAndTranslate(
  var scale: Float = 0f,
  val screenTranslate: PointF = PointF(0f, 0f)
) {
  fun reset() {
    scale = 0f
    screenTranslate.set(0f, 0f)
  }
}