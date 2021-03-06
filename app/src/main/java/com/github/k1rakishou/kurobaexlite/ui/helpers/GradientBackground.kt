package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Taken from https://github.com/android/nowinandroid/blob/main/core-ui/src/main/java/com/google/samples/apps/nowinandroid/core/ui/component/Background.kt
 * */

@Composable
fun GradientBackground(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val currentTopColor = chanTheme.gradientTopColor.copy(alpha = 0.035f)
  val currentBottomColor = chanTheme.gradientBottomColor.copy(alpha = 0.035f)
  val behindGradientColor = chanTheme.behindGradientColor

  Surface(
    modifier = modifier,
    color = behindGradientColor,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          // Compute the start and end coordinates such that the gradients are angled 11.06
          // degrees off the vertical axis
          val offset = size.height * kotlin.math.tan(
            Math
              .toRadians(11.06)
              .toFloat()
          )

          val start = Offset(size.width / 2 + offset / 2, 0f)
          val end = Offset(size.width / 2 - offset / 2, size.height)

          // Create the top gradient that fades out after the halfway point vertically
          val topGradient = Brush.linearGradient(
            0f to currentTopColor,
            0.724f to Color.Transparent,
            start = start,
            end = end,
          )
          // Create the bottom gradient that fades in before the halfway point vertically
          val bottomGradient = Brush.linearGradient(
            0.2552f to Color.Transparent,
            1f to currentBottomColor,
            start = start,
            end = end,
          )

          onDrawBehind {
            // There is overlap here, so order is important
            drawRect(topGradient)
            drawRect(bottomGradient)
          }
        }
    ) {
      content()
    }
  }
}