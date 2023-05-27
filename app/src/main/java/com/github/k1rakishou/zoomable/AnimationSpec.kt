package com.github.k1rakishou.zoomable

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset

data class AnimationSpec(
  val flingAnimationSpec: DecayAnimationSpec<Offset> = exponentialDecay(frictionMultiplier = 2f),
  val zoomAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 250)
)