package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun KurobaComposeFadeIn(
  modifier: Modifier = Modifier,
  durationMillis: Int = 250,
  delayMillis: Int = 100,
  key1: Any = Unit,
  content: @Composable () -> Unit
) {
  var visible by remember(key1 = key1) { mutableStateOf(false) }

  AnimatedVisibility(
    modifier = modifier,
    visible = visible,
    enter = fadeIn(animationSpec = tween(durationMillis = durationMillis, delayMillis = delayMillis)),
    exit = fadeOut(animationSpec = snap())
  ) {
    content()
  }

  LaunchedEffect(
    key1 = key1,
    block = { visible = true }
  )
}