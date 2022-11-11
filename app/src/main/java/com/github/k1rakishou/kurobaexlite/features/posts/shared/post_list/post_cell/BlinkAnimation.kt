package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@Composable
fun BlinkAnimation(
  postCellDefaultBgColor: Color,
  postCellBlinkBgColor: Color,
  postDescriptor: PostDescriptor,
  postBlinkAnimationState: PostBlinkAnimationState,
  postCellBackgroundColorAnimatable: Animatable<Color, AnimationVector4D>
) {
  LaunchedEffect(
    key1 = Unit,
    block = {
      if (postBlinkAnimationState.blinkEvents.value == postDescriptor) {
        runBlinkAnimation(
          postCellBackgroundColorAnimatable = postCellBackgroundColorAnimatable,
          postCellBlinkBgColor = postCellBlinkBgColor,
          postCellDefaultBgColor = postCellDefaultBgColor,
          postBlinkAnimationState = postBlinkAnimationState
        )
      }

      postBlinkAnimationState.blinkEvents.collectLatest { blinkingPostDescriptor ->
        if (blinkingPostDescriptor != postDescriptor) {
          if (postCellBackgroundColorAnimatable.value != postCellDefaultBgColor) {
            withContext(NonCancellable) { postCellBackgroundColorAnimatable.snapTo(postCellDefaultBgColor) }
          }

          return@collectLatest
        }

        runBlinkAnimation(
          postCellBackgroundColorAnimatable = postCellBackgroundColorAnimatable,
          postCellBlinkBgColor = postCellBlinkBgColor,
          postCellDefaultBgColor = postCellDefaultBgColor,
          postBlinkAnimationState = postBlinkAnimationState
        )
      }
    }
  )
}

private suspend fun runBlinkAnimation(
  postCellBackgroundColorAnimatable: Animatable<Color, AnimationVector4D>,
  postCellBlinkBgColor: Color,
  postCellDefaultBgColor: Color,
  postBlinkAnimationState: PostBlinkAnimationState
) {
  try {
    repeat(3) {
      postCellBackgroundColorAnimatable.animateTo(
        targetValue = postCellBlinkBgColor,
        animationSpec = tween(durationMillis = 150)
      )

      postCellBackgroundColorAnimatable.animateTo(
        targetValue = postCellDefaultBgColor,
        animationSpec = tween(durationMillis = 150)
      )
    }
  } finally {
    withContext(NonCancellable) {
      postBlinkAnimationState.stopBlinking()
      postCellBackgroundColorAnimatable.snapTo(postCellDefaultBgColor)
    }
  }
}