package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
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
          val scaleStart = .7f
          val scaleEnd = 1f

          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = 250,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { animationProgress, _ ->
            scaleAnimated = lerpFloat(scaleStart, scaleEnd, animationProgress)
            alphaAnimated = animationProgress
          }
        }
        is NavigationRouter.ScreenUpdate.Pop -> {
          val scaleStart = 1f
          val scaleEnd = .7f

          animate(
            initialValue = 1f,
            targetValue = 0f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = 250,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { animationProgress, _ ->
            scaleAnimated = lerpFloat(scaleStart, scaleEnd, animationProgress)
            alphaAnimated = animationProgress
          }
        }
        is NavigationRouter.ScreenUpdate.Replace -> {
          scaleAnimated = 1f

          animate(
            initialValue = 1f,
            targetValue = 0f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = 250,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { animationProgress, _ ->
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