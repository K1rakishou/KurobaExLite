package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.helpers.util.lerpFloat
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen

@Composable
fun ScreenTransition(
  composeScreen: ComposeScreen,
  screenAnimation: NavigationRouter.ScreenAnimation,
  onScreenAnimationFinished: suspend (NavigationRouter.ScreenAnimation) -> Unit,
  content: @Composable () -> Unit
) {
  val scaleInitial = when (screenAnimation) {
    is NavigationRouter.ScreenAnimation.Push -> .9f
    is NavigationRouter.ScreenAnimation.Pop -> 1f
    is NavigationRouter.ScreenAnimation.Fade -> 1f
    is NavigationRouter.ScreenAnimation.Set -> 1f
    is NavigationRouter.ScreenAnimation.Remove -> 0f
  }

  val alphaInitial = when (screenAnimation) {
    is NavigationRouter.ScreenAnimation.Push -> 0f
    is NavigationRouter.ScreenAnimation.Pop -> 1f
    is NavigationRouter.ScreenAnimation.Fade -> {
      when (screenAnimation.fadeType) {
        NavigationRouter.ScreenAnimation.FadeType.In -> 0f
        NavigationRouter.ScreenAnimation.FadeType.Out -> 1f
      }
    }
    is NavigationRouter.ScreenAnimation.Set -> 1f
    is NavigationRouter.ScreenAnimation.Remove -> 0f
  }

  val canRenderInitial = when (screenAnimation) {
    is NavigationRouter.ScreenAnimation.Push -> false
    is NavigationRouter.ScreenAnimation.Pop -> true
    is NavigationRouter.ScreenAnimation.Fade -> {
      when (screenAnimation.fadeType) {
        NavigationRouter.ScreenAnimation.FadeType.In -> false
        NavigationRouter.ScreenAnimation.FadeType.Out -> true
      }
    }
    is NavigationRouter.ScreenAnimation.Set -> true
    is NavigationRouter.ScreenAnimation.Remove -> false
  }

  val scaleAnimated = remember(key1 = screenAnimation) { mutableFloatStateOf(scaleInitial) }
  val alphaAnimated = remember(key1 = screenAnimation) { mutableFloatStateOf(alphaInitial) }
  var canRender by remember(key1 = screenAnimation) { mutableStateOf(canRenderInitial) }

  LaunchedEffect(
    key1 = composeScreen.screenKey,
    key2 = screenAnimation,
    block = {
      val homeNavigationScreen = composeScreen as? HomeNavigationScreen<*>

      when (screenAnimation) {
        is NavigationRouter.ScreenAnimation.Push -> {
          val scaleStart = .9f
          val scaleEnd = 1f
          canRender = true

          try {
            animate(
              initialValue = 0f,
              targetValue = 1f,
              initialVelocity = 0f,
              animationSpec = FloatTweenSpec(
                duration = screenAnimation.animationDuration,
                delay = 0,
                easing = FastOutSlowInEasing
              )
            ) { animationProgress, _ ->
              scaleAnimated.floatValue = lerpFloat(scaleStart, scaleEnd, animationProgress)
              alphaAnimated.floatValue = animationProgress

              homeNavigationScreen?.updateAnimationProgress(animationProgress)
            }
          } finally {
            onScreenAnimationFinished(screenAnimation)
          }
        }
        is NavigationRouter.ScreenAnimation.Pop -> {
          val scaleStart = 1f
          val scaleEnd = .9f

          try {
            animate(
              initialValue = 0f,
              targetValue = 1f,
              initialVelocity = 0f,
              animationSpec = FloatTweenSpec(
                duration = screenAnimation.animationDuration,
                delay = 0,
                easing = FastOutSlowInEasing
              )
            ) { animationProgress, _ ->
              scaleAnimated.floatValue = lerpFloat(scaleStart, scaleEnd, animationProgress)
              alphaAnimated.floatValue = 1f - animationProgress

              homeNavigationScreen?.updateAnimationProgress(1f - animationProgress)
            }
          } finally {
            onScreenAnimationFinished(screenAnimation)
          }

          canRender = false
        }
        is NavigationRouter.ScreenAnimation.Fade -> {
          scaleAnimated.floatValue = 1f

          val initialValue = when (screenAnimation.fadeType) {
            NavigationRouter.ScreenAnimation.FadeType.In -> 0f
            NavigationRouter.ScreenAnimation.FadeType.Out -> 1f
          }

          val targetValue = when (screenAnimation.fadeType) {
            NavigationRouter.ScreenAnimation.FadeType.In -> 1f
            NavigationRouter.ScreenAnimation.FadeType.Out -> 0f
          }

          canRender = true

          try {
            animate(
              initialValue = initialValue,
              targetValue = targetValue,
              initialVelocity = 0f,
              animationSpec = FloatTweenSpec(
                duration = screenAnimation.animationDuration,
                delay = 0,
                easing = FastOutSlowInEasing
              )
            ) { animationProgress, _ ->
              alphaAnimated.floatValue = animationProgress
              homeNavigationScreen?.updateAnimationProgress(animationProgress)
            }
          } finally {
            onScreenAnimationFinished(screenAnimation)
          }

          canRender = when (screenAnimation.fadeType) {
            NavigationRouter.ScreenAnimation.FadeType.In -> true
            NavigationRouter.ScreenAnimation.FadeType.Out -> false
          }
        }
        is NavigationRouter.ScreenAnimation.Set -> {
          scaleAnimated.floatValue = 1f
          alphaAnimated.floatValue = 1f
          homeNavigationScreen?.updateAnimationProgress(1f)
          onScreenAnimationFinished(screenAnimation)
          canRender = true
        }
        is NavigationRouter.ScreenAnimation.Remove -> {
          scaleAnimated.floatValue = 0f
          alphaAnimated.floatValue = 0f
          homeNavigationScreen?.updateAnimationProgress(0f)
          onScreenAnimationFinished(screenAnimation)
          canRender = false
        }
      }
    }
  )

  if (canRender) {
    if (composeScreen is FloatingComposeScreen) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = alphaAnimated.floatValue }
          .drawBehind { drawRect(composeScreen.backgroundColor) }
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              scaleX = scaleAnimated.floatValue
              scaleY = scaleAnimated.floatValue
            }
        ) {
          content()
        }
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            alpha = alphaAnimated.floatValue
            scaleX = scaleAnimated.floatValue
            scaleY = scaleAnimated.floatValue
          }
      ) {
        content()
      }
    }
  }
}