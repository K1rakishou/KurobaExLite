package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list

import android.os.SystemClock
import androidx.compose.animation.Animatable
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.hash.Murmur3Hash
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PreviousPostDataInfo

private val animationTranslationDelta = 100.dp
private val insertAnimationTotalDurationMs = 200
private val updateAnimationTotalDurationMs = 800
private val insertAnimationMaxTimeoutMs = 500

@Composable
internal fun PostCellContainerAnimated(
  animateInsertion: Boolean,
  animateUpdate: Boolean,
  isCatalogMode: Boolean,
  postCellData: PostCellData,
  currentlyOpenedThread: ThreadDescriptor?,
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  var currentAnimation by remember { mutableStateOf<AnimationType?>(null) }
  val contentMovable = remember { movableContentOf { content() } }

  if (animateInsertion || animateUpdate || currentAnimation != null) {
    if (animateInsertion || currentAnimation == AnimationType.Insertion) {
      SideEffect { currentAnimation = AnimationType.Insertion }

      PostCellContainerInsertAnimation(
        onAnimationFinished = { currentAnimation = null },
        content = contentMovable
      )

      return
    }

    if (animateUpdate || currentAnimation == AnimationType.Update) {
      SideEffect { currentAnimation = AnimationType.Update }

      PostCellContainerUpdateAnimation(
        onAnimationFinished = { currentAnimation = null },
        content = contentMovable
      )

      return
    }
  }

  val bgColor = remember(key1 = isCatalogMode, key2 = currentlyOpenedThread) {
    if (isCatalogMode && currentlyOpenedThread == postCellData.postDescriptor.threadDescriptor) {
      chanTheme.highlighterColorCompose.copy(alpha = 0.3f)
    } else {
      Color.Unspecified
    }
  }

  Box(modifier = Modifier.background(bgColor)) {
    contentMovable()
  }
}

@Composable
internal fun PostCellContainerUpdateAnimation(
  onAnimationFinished: () -> Unit,
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val bgColorAnimatable = remember { Animatable(Color.Unspecified) }

  val startColor = chanTheme.backColorCompose
  val endColor = chanTheme.selectedOnBackColorCompose

  LaunchedEffect(
    key1 = Unit,
    block = {
      try {
        bgColorAnimatable.snapTo(startColor)
        bgColorAnimatable.animateTo(endColor, tween(durationMillis = 400, easing = LinearEasing))
        bgColorAnimatable.animateTo(startColor, tween(durationMillis = 400, easing = LinearEasing))
      } finally {
        bgColorAnimatable.snapTo(Color.Unspecified)
        onAnimationFinished()
      }
    })

  val bgColor by bgColorAnimatable.asState()

  Box(modifier = Modifier.background(bgColor)) {
    content()
  }
}

@Composable
internal fun PostCellContainerInsertAnimation(
  onAnimationFinished: () -> Unit,
  content: @Composable () -> Unit
) {
  val animationTranslationDeltaPx = with(LocalDensity.current) {
    remember(key1 = animationTranslationDelta) {
      animationTranslationDelta.toPx()
    }
  }

  val chanTheme = LocalChanTheme.current
  val startColor = chanTheme.selectedOnBackColorCompose
  val endColor = chanTheme.backColorCompose

  val translationAnimatable = remember { Animatable(animationTranslationDeltaPx, Float.VectorConverter) }
  val alphaAnimatable = remember { Animatable(0.5f, Float.VectorConverter) }
  val bgColorAnimatable = remember { Animatable(startColor, (Color.VectorConverter)(startColor.colorSpace)) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      try {
        translationAnimatable.animateTo(
          0f,
          tween(durationMillis = insertAnimationTotalDurationMs, easing = LinearEasing)
        )
        alphaAnimatable.animateTo(
          1f,
          tween(durationMillis = insertAnimationTotalDurationMs, easing = LinearEasing)
        )
        bgColorAnimatable.animateTo(
          endColor,
          tween(durationMillis = insertAnimationTotalDurationMs, easing = LinearEasing)
        )
      } finally {
        translationAnimatable.snapTo(0f)
        alphaAnimatable.snapTo(1f)
        bgColorAnimatable.snapTo(Color.Unspecified)

        onAnimationFinished()
      }
    })

  val translationAnimated by translationAnimatable.asState()
  val alphaAnimated by alphaAnimatable.asState()
  val colorAnimated by bgColorAnimatable.asState()

  Box(
    modifier = Modifier
      .background(colorAnimated)
      .graphicsLayer {
        translationY = translationAnimated
        alpha = alphaAnimated
      }
  ) {
    content()
  }
}

internal fun canAnimateUpdate(
  previousPostDataInfoMap: MutableMap<PostDescriptor, PreviousPostDataInfo>?,
  postCellData: PostCellData,
  searchQuery: String?,
  inPopup: Boolean,
  rememberedHashForListAnimations: Murmur3Hash?,
  postsParsedOnce: Boolean
): Boolean {
  if (previousPostDataInfoMap == null || searchQuery != null || !postsParsedOnce || inPopup) {
    return false
  }

  val previousPostDataInfo = previousPostDataInfoMap[postCellData.postDescriptor]
  if (previousPostDataInfo == null) {
    return false
  }

  if (rememberedHashForListAnimations == null) {
    return false
  }

  if (previousPostDataInfo.hash == rememberedHashForListAnimations) {
    return false
  }

  return (previousPostDataInfo.time + updateAnimationTotalDurationMs) >= SystemClock.elapsedRealtime()
}

internal fun canAnimateInsertion(
  previousPostDataInfoMap: MutableMap<PostDescriptor, PreviousPostDataInfo>?,
  postCellData: PostCellData,
  searchQuery: String?,
  inPopup: Boolean,
  postsParsedOnce: Boolean
): Boolean {
  if (previousPostDataInfoMap == null || searchQuery != null || !postsParsedOnce || inPopup) {
    return false
  }

  val previousPostDataInfo = previousPostDataInfoMap[postCellData.postDescriptor]
  if (previousPostDataInfo == null) {
    return true
  }

  if (previousPostDataInfo.alreadyAnimatedInsertion) {
    return false
  }

  val canAnimate = (previousPostDataInfo.time + insertAnimationMaxTimeoutMs) >= SystemClock.elapsedRealtime()
  if (canAnimate) {
    previousPostDataInfoMap[postCellData.postDescriptor] =
      previousPostDataInfo.copy(alreadyAnimatedInsertion = true)
  }

  return canAnimate
}

internal enum class AnimationType {
  Insertion,
  Update
}