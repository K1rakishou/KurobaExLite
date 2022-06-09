package com.github.k1rakishou.kurobaexlite.features.media.helpers

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme

@Composable
internal fun DisplayLoadingProgressIndicator(restartIndex: Int, progress: Float) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val arcSize = with(density) { remember { 42.dp.toPx() } }
  val width = with(density) { remember { 4.dp.toPx() } }
  val textSize = with(density) { 14.sp.toPx() }

  val style = remember { Stroke(width = width) }
  val rotationAnimateable = remember { Animatable(0f) }
  val animationSpec = remember {
    infiniteRepeatable<Float>(animation = tween(durationMillis = 2500), repeatMode = RepeatMode.Restart)
  }
  val textPaint = remember {
    TextPaint().apply {
      this.textSize = textSize
      this.color = Color.WHITE
      this.style = Paint.Style.FILL
      this.setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
  }

  val maxRestarts = MediaViewerScreenViewModel.MAX_RETRIES

  LaunchedEffect(
    key1 = Unit,
    block = { rotationAnimateable.animateTo(targetValue = 1f, animationSpec = animationSpec) }
  )

  val rotation by rotationAnimateable.asState()
  val text = "${restartIndex}/$maxRestarts"

  val textSizeMeasured = remember(key1 = text) {
    val rect = Rect()
    textPaint.getTextBounds(text, 0, text.length, rect)

    return@remember Size(
      rect.width().toFloat(),
      rect.height().toFloat()
    )
  }

  Canvas(
    modifier = Modifier.fillMaxSize(),
    onDraw = {
      val center = this.size.center
      val topLeft = Offset(center.x - (arcSize / 2f), center.y - (arcSize / 2f))
      val size = Size(arcSize, arcSize)

      rotate(degrees = rotation * 360f, pivot = center) {
        drawArc(
          color = chanTheme.accentColor,
          startAngle = 0f,
          sweepAngle = 360f * progress,
          useCenter = false,
          topLeft = topLeft,
          size = size,
          style = style
        )
      }

      if (restartIndex > 0) {
        drawContext.canvas.nativeCanvas.drawText(
          text,
          center.x - (textSizeMeasured.width / 2f),
          center.y + (textSizeMeasured.height / 2f),
          textPaint
        )
      }
    }
  )
}