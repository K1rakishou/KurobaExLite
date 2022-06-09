package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme

fun Modifier.drawDragLongtapDragGestureZone(
  drawerLongtapGestureWidthZonePx: Float,
  longtapDragGestureDetected: Boolean,
  resetFlag: () -> Unit
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val animatable = remember { Animatable(initialValue = 0f) }

    LaunchedEffect(
      key1 = longtapDragGestureDetected,
      block = {
        if (!longtapDragGestureDetected) {
          animatable.snapTo(0f)
          return@LaunchedEffect
        }

        try {
          animatable.animateTo(.7f, tween(durationMillis = 100))
          animatable.animateTo(0f, tween(durationMillis = 350))
        } finally {
          resetFlag()
        }
      }
    )

    val dragGestureZoneAlpha by animatable.asState()

    return@composed drawWithContent {
      drawContent()

      if (dragGestureZoneAlpha > 0f) {
        drawRect(
          color = chanTheme.accentColor,
          topLeft = Offset.Zero,
          size = Size(
            width = drawerLongtapGestureWidthZonePx,
            height = size.height
          ),
          alpha = dragGestureZoneAlpha
        )
      }
    }
  }
}