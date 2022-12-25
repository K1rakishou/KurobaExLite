package com.github.k1rakishou.kurobaexlite.ui.elements

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

/**
 * Taken from https://github.com/android/nowinandroid/blob/c19b8b93191c0a627d4c9e030d2b5cc2f62ab8be/core/designsystem/src/main/java/com/google/samples/apps/nowinandroid/core/designsystem/component/LoadingWheel.kt
 * */

private const val ROTATION_TIME = 12000
private const val NUM_OF_LINES = 12

@Composable
fun LoadingWheel(
  modifier: Modifier = Modifier,
  baseLineColor: Color,
  progressLineColor: Color,
  contentDesc: String
) {
  val infiniteTransition = rememberInfiniteTransition()

  // Specifies the float animation for slowly drawing out the lines on entering
  val startValue = if (LocalInspectionMode.current) 0F else 1F
  val floatAnimValues = (0 until NUM_OF_LINES).map { remember { Animatable(startValue) } }
  LaunchedEffect(floatAnimValues) {
    (0 until NUM_OF_LINES).map { index ->
      launch {
        floatAnimValues[index].animateTo(
          targetValue = 0F,
          animationSpec = tween(
            durationMillis = 100,
            easing = FastOutSlowInEasing,
            delayMillis = 40 * index
          )
        )
      }
    }
  }

  // Specifies the rotation animation of the entire Canvas composable
  val rotationAnim by infiniteTransition.animateFloat(
    initialValue = 0F,
    targetValue = 360F,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = ROTATION_TIME, easing = LinearEasing)
    )
  )

  val colorAnimValues = (0 until NUM_OF_LINES).map { index ->
    infiniteTransition.animateColor(
      initialValue = baseLineColor,
      targetValue = baseLineColor,
      animationSpec = infiniteRepeatable(
        animation = keyframes {
          durationMillis = ROTATION_TIME / 2
          progressLineColor at ROTATION_TIME / NUM_OF_LINES / 2 with LinearEasing
          baseLineColor at ROTATION_TIME / NUM_OF_LINES with LinearEasing
        },
        repeatMode = RepeatMode.Restart,
        initialStartOffset = StartOffset(ROTATION_TIME / NUM_OF_LINES / 2 * index)
      )
    )
  }

  // Draws out the LoadingWheel Canvas composable and sets the animations
  Canvas(
    modifier = modifier
      .graphicsLayer { rotationZ = rotationAnim }
      .semantics { contentDescription = contentDesc }
  ) {
    repeat(NUM_OF_LINES) { index ->
      rotate(degrees = index * 30f) {
        drawLine(
          color = colorAnimValues[index].value,
          // Animates the initially drawn 1 pixel alpha from 0 to 1
          alpha = if (floatAnimValues[index].value < 1f) 1f else 0f,
          strokeWidth = 4F,
          cap = StrokeCap.Round,
          start = Offset(size.width / 2, size.height / 4),
          end = Offset(size.width / 2, floatAnimValues[index].value * size.height / 4)
        )
      }
    }
  }
}