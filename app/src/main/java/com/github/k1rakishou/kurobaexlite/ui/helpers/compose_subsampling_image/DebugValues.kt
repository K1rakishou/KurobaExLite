package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import android.graphics.Paint
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal class DebugValues(density: Density) {
  val debugTextPaint by lazy {
    Paint().apply {
      textSize = with(density) { 10.dp.toPx() }
      color = android.graphics.Color.GREEN
      style = Paint.Style.FILL
      setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
    }
  }
  val borderWidthPx by lazy { with(density) { 1.dp.toPx() } }
}