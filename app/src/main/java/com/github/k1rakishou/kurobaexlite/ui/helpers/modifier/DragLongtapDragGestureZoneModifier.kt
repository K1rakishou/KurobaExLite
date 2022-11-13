package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest

fun Modifier.drawDragLongtapDragGestureZone(
  drawerLongtapGestureWidthZonePx: Float,
  drawerLongtapDragGestureZoneState: DrawerLongtapDragGestureZoneState,
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val animatable = remember { Animatable(initialValue = 0f) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        drawerLongtapDragGestureZoneState.longtapDragGestureDetected.collectLatest { longtapDragGestureDetected ->
          if (!longtapDragGestureDetected) {
            animatable.snapTo(0f)
            return@collectLatest
          }

          try {
            animatable.animateTo(.7f, tween(durationMillis = 100))
            animatable.animateTo(0f, tween(durationMillis = 350))
          } finally {
            drawerLongtapDragGestureZoneState.onGestureStateChanged(false)
          }
        }
      }
    )

    val dragGestureZoneAlpha by animatable.asState()

    return@composed drawWithContent {
      drawContent()

      if (dragGestureZoneAlpha > 0f) {
        drawRect(
          color = chanTheme.accentColor,
          topLeft = Offset.Zero,
          size = Size(
            width = drawerLongtapGestureWidthZonePx,
            height = size.height
          ),
          alpha = dragGestureZoneAlpha
        )
      }
    }
  }
}

@Composable
fun rememberDrawerLongtapDragGestureZoneState(): DrawerLongtapDragGestureZoneState {
  return remember { DrawerLongtapDragGestureZoneState() }
}

@Stable
class DrawerLongtapDragGestureZoneState {

  private val _longtapDragGestureDetected = MutableSharedFlow<Boolean>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val longtapDragGestureDetected: SharedFlow<Boolean>
    get() = _longtapDragGestureDetected.asSharedFlow()

  fun onGestureStateChanged(longtapDetected: Boolean) {
    _longtapDragGestureDetected.tryEmit(longtapDetected)
  }

}