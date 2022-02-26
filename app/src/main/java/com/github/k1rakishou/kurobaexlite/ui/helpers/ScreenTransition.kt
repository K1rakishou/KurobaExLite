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
  content: @Composable () -> Unit
) {
  val scaleInitial = when (screenUpdate) {
    is NavigationRouter.ScreenUpdate.Push -> .85f
    is NavigationRouter.ScreenUpdate.Pop -> 1f
    is NavigationRouter.ScreenUpdate.Fade -> 1f
    is NavigationRouter.ScreenUpdate.Set -> 1f
  }

  val alphaInitial = when (screenUpdate) {
    is NavigationRouter.ScreenUpdate.Push -> 0f
    is NavigationRouter.ScreenUpdate.Pop -> 1f
    is NavigationRouter.ScreenUpdate.Fade -> {
      when (screenUpdate.fadeType) {
        NavigationRouter.ScreenUpdate.FadeType.In -> 0f
        NavigationRouter.ScreenUpdate.FadeType.Out -> 1f
      }
    }
    is NavigationRouter.ScreenUpdate.Set -> 1f
  }

  val canRenderInitial = when (screenUpdate) {
    is NavigationRouter.ScreenUpdate.Push -> false
    is NavigationRouter.ScreenUpdate.Pop -> true
    is NavigationRouter.ScreenUpdate.Fade -> {
      when (screenUpdate.fadeType) {
        NavigationRouter.ScreenUpdate.FadeType.In -> false
        NavigationRouter.ScreenUpdate.FadeType.Out -> true
      }
    }
    is NavigationRouter.ScreenUpdate.Set -> true
  }

  var scaleAnimated by remember(key1 = screenUpdate) { mutableStateOf(scaleInitial) }
  var alphaAnimated by remember(key1 = screenUpdate) { mutableStateOf(alphaInitial) }
  var canRender by remember(key1 = screenUpdate) { mutableStateOf(canRenderInitial) }

  val animationDuration = 200

  LaunchedEffect(
    key1 = screenUpdate,
    block = {
      when (screenUpdate) {
        is NavigationRouter.ScreenUpdate.Push -> {
          val scaleStart = .85f
          val scaleEnd = 1f
          canRender = true

          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = animationDuration,
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
          val scaleEnd = .85f

          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = animationDuration,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { animationProgress, _ ->
            scaleAnimated = lerpFloat(scaleStart, scaleEnd, animationProgress)
            alphaAnimated = 1f - animationProgress
          }

          canRender = false
        }
        is NavigationRouter.ScreenUpdate.Fade -> {
          scaleAnimated = 1f

          val initialValue = when (screenUpdate.fadeType) {
            NavigationRouter.ScreenUpdate.FadeType.In -> 0f
            NavigationRouter.ScreenUpdate.FadeType.Out -> 1f
          }

          val targetValue = when (screenUpdate.fadeType) {
            NavigationRouter.ScreenUpdate.FadeType.In -> 1f
            NavigationRouter.ScreenUpdate.FadeType.Out -> 0f
          }

          canRender = true

          animate(
            initialValue = initialValue,
            targetValue = targetValue,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = animationDuration,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { animationProgress, _ ->
            alphaAnimated = animationProgress
          }

          canRender = when (screenUpdate.fadeType) {
            NavigationRouter.ScreenUpdate.FadeType.In -> true
            NavigationRouter.ScreenUpdate.FadeType.Out -> false
          }
        }
        is NavigationRouter.ScreenUpdate.Set -> {
          scaleAnimated = 1f
          alphaAnimated = 1f
          canRender = true
        }
      }
    }
  )

  if (canRender) {
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
}