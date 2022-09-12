package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

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

  var visible by rememberSaveable(key1) { mutableStateOf(false) }

  AnimatedVisibility(
    modifier = modifier,
    visible = visible,
    enter = fadeIn(animationSpec = tween(durationMillis = durationMillis, delayMillis = delayMillis)),
    exit = fadeOut(animationSpec = snap())
  ) {
    movableContent()
  }

  DisposableEffect(
    key1 = key1,
    effect = {
      visible = true
      onDispose { visible = false }
    }
  )
}