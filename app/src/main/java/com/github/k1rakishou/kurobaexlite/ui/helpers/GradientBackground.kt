package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
  content: @Composable BoxScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Surface(
    modifier = modifier,
    color = Color.Unspecified,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          val currentTopColor = chanTheme.gradientTopColor
          val currentBottomColor = chanTheme.gradientBottomColor
          val behindGradientColor = chanTheme.behindGradientColor

          if (currentTopColor == currentBottomColor) {
            return@drawWithCache onDrawBehind {
              drawRect(behindGradientColor)
            }
          }

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
            drawRect(behindGradientColor)

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