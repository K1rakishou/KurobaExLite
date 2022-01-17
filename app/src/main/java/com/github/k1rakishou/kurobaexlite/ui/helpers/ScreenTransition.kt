package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

@Composable
fun ScreenTransition(
  screenUpdate: NavigationRouter.ScreenUpdate,
  onTransitionFinished: (NavigationRouter.ScreenUpdate) -> Unit,
  content: @Composable () -> Unit
) {
  var scaleAnimated by remember { mutableStateOf(0f) }
  var alphaAnimated by remember { mutableStateOf(0f) }

  LaunchedEffect(
    key1 = screenUpdate,
    block = {
      when (screenUpdate) {
        is NavigationRouter.ScreenUpdate.Push -> {
          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = tween(250)
          ) { animationProgress, _ ->
            scaleAnimated = 1f - (0.5f - (animationProgress / 2f))
            alphaAnimated = animationProgress
          }
        }
        is NavigationRouter.ScreenUpdate.Pop -> {
          animate(
            initialValue = 1f,
            targetValue = 0f,
            initialVelocity = 0f,
            animationSpec = tween(250)
          ) { animationProgress, _ ->
            scaleAnimated = 1f - (0.5f - (animationProgress / 2f))
            alphaAnimated = animationProgress
          }
        }
      }

      onTransitionFinished(screenUpdate)
    }
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .graphicsLayer(
        alpha = alphaAnimated,
        scaleX = scaleAnimated,
        scaleY = scaleAnimated
      )
  ) {
    content()
  }
}