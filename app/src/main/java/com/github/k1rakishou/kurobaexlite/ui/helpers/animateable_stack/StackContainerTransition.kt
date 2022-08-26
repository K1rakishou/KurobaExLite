package com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.k1rakishou.kurobaexlite.helpers.util.lerpFloat

@Composable
internal fun <T : DisposableElement> StackContainerTransition(
  animationDuration: Int = 250,
  animatingChange: StackContainerChange<T>,
  onAnimationFinished: suspend () -> Unit,
  content: @Composable () -> Unit
) {
  val scaleInitial = when (val animation = animatingChange.animation) {
    is StackContainerAnimation.Remove -> 0f
    is StackContainerAnimation.Set -> 1f
    is StackContainerAnimation.Push -> .85f
    is StackContainerAnimation.Pop -> 1f
    is StackContainerAnimation.Fade -> {
      when (animation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 0f
        is StackContainerAnimation.FadeType.Out -> 1f
      }
    }
  }

  val alphaInitial = when (val animation = animatingChange.animation) {
    is StackContainerAnimation.Remove -> 0f
    is StackContainerAnimation.Set -> 1f
    is StackContainerAnimation.Push -> 0f
    is StackContainerAnimation.Pop -> 1f
    is StackContainerAnimation.Fade -> {
      when (animation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 0f
        is StackContainerAnimation.FadeType.Out -> 1f
      }
    }
  }

  val canRenderInitial = when (val animation = animatingChange.animation) {
    is StackContainerAnimation.Remove -> true
    is StackContainerAnimation.Set -> false
    is StackContainerAnimation.Push -> false
    is StackContainerAnimation.Pop -> true
    is StackContainerAnimation.Fade -> {
      when (animation.fadeType) {
        is StackContainerAnimation.FadeType.In -> false
        is StackContainerAnimation.FadeType.Out -> true
      }
    }
  }

  var scaleAnimated by remember { mutableStateOf(scaleInitial) }
  var alphaAnimated by remember { mutableStateOf(alphaInitial) }
  var canRender by remember { mutableStateOf(canRenderInitial) }

  LaunchedEffect(
    key1 = animatingChange,
    block = {
      try {
        animateInternal(
          stackContainerChange = animatingChange,
          animationDuration = animationDuration,
          onCanRenderChanged = { newValue -> canRender = newValue },
          onScaleChanged = { newValue -> scaleAnimated = newValue },
          onAlphaChanged = { newValue -> alphaAnimated = newValue },
        )
      } finally {
        onAnimationFinished()
      }
    }
  )

  if (canRender) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          alpha = alphaAnimated
          scaleX = scaleAnimated
          scaleY = scaleAnimated
        }
    ) {
      content()
    }
  }
}

private suspend fun <T : DisposableElement> animateInternal(
  stackContainerChange: StackContainerChange<T>,
  animationDuration: Int,
  onCanRenderChanged: (Boolean) -> Unit,
  onScaleChanged: (Float) -> Unit,
  onAlphaChanged: (Float) -> Unit
) {
  when (val animation = stackContainerChange.animation) {
    is StackContainerAnimation.Remove -> {
      onCanRenderChanged(false)
    }
    is StackContainerAnimation.Set -> {
      onCanRenderChanged(true)
    }
    is StackContainerAnimation.Push -> {
      val scaleStart = .85f
      val scaleEnd = 1f
      onCanRenderChanged(true)

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
        onScaleChanged(lerpFloat(scaleStart, scaleEnd, animationProgress))
        onAlphaChanged(animationProgress)
      }
    }
    is StackContainerAnimation.Pop -> {
      val scaleStart = 1f
      val scaleEnd = .85f

      animate(
        initialValue = 1f,
        targetValue = 0f,
        initialVelocity = 0f,
        animationSpec = FloatTweenSpec(
          duration = animationDuration,
          delay = 0,
          easing = FastOutSlowInEasing
        )
      ) { animationProgress, _ ->
        onScaleChanged(lerpFloat(scaleStart, scaleEnd, animationProgress))
        onAlphaChanged(animationProgress)
      }

      onCanRenderChanged(true)
    }
    is StackContainerAnimation.Fade -> {
      val initialValue = when (animation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 0f
        is StackContainerAnimation.FadeType.Out -> 1f
      }

      val targetValue = when (animation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 1f
        is StackContainerAnimation.FadeType.Out -> 0f
      }

      onCanRenderChanged(true)

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
        onAlphaChanged(animationProgress)
      }

      val canRender = when (animation.fadeType) {
        is StackContainerAnimation.FadeType.In -> true
        is StackContainerAnimation.FadeType.Out -> false
      }

      onCanRenderChanged(canRender)
    }
  }
}