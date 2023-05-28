package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberSaveableResettable

@Composable
fun KurobaComposeFadeIn(
  modifier: Modifier = Modifier,
  durationMillis: Int = 200,
  delayMillis: Int = 100,
  enabled: Boolean = true,
  key1: Any = Unit,
  content: @Composable () -> Unit
) {
  val movableContent = remember { movableContentOf { content() } }

  if (!enabled) {
    movableContent()
    return
  }

  var visible by rememberSaveableResettable(key1) { mutableStateOf(false) }
  val contentAlpha = remember { mutableFloatStateOf(0f) }

  Box(
    modifier = modifier.then(
      Modifier.graphicsLayer { alpha = contentAlpha.value }
    ),
  ) {
    movableContent()
  }

  LaunchedEffect(
    key1 = visible,
    block = {
      val start = if (visible) 0f else 1f
      val end = if (visible) 1f else 0f

      animate(
        initialValue = start,
        targetValue = end,
        initialVelocity = 0f,
        animationSpec = tween(durationMillis, delayMillis)
      ) { progress, _ ->
        contentAlpha.value = progress
      }
    }
  )

  DisposableEffect(
    key1 = key1,
    effect = {
      visible = true
      onDispose { visible = false }
    }
  )
}