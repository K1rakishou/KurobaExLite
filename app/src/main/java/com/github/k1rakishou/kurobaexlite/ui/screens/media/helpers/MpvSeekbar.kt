package com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

@Composable
fun MpvSeekbar(
  modifier: Modifier = Modifier,
  trackColor: Color,
  thumbColorNormal: Color,
  thumbColorPressed: Color,
  trackHeightDp: Dp = 1.dp,
  thumbRadiusNormalDp: Dp = 12.dp,
  thumbRadiusPressedDp: Dp = 16.dp,
  slideOffsetState: MutableState<Float>,
  demuxerCachePercents: Float?,
  onValueChange: (Float) -> Unit,
  slideSteps: Int? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
  val density = LocalDensity.current
  val slideOffset by slideOffsetState

  val trackHeightPx = with(density) { remember(trackHeightDp) { trackHeightDp.toPx() } }
  val thumbRadiusNormalPx = with(density) { remember(thumbRadiusNormalDp) { thumbRadiusNormalDp.toPx() } }
  val thumbRadiusPressedPx = with(density) { remember(thumbRadiusPressedDp) { thumbRadiusPressedDp.toPx() } }
  val notLoadedYetPartColor = remember(trackColor) { trackColor.copy(alpha = 0.6f) }

  BoxWithConstraints(modifier) {
    val maxWidthPx = constraints.maxWidth.toFloat()
    val rawOffsetState = remember { mutableStateOf(0f) }
    var rawOffsetInPx by rawOffsetState

    fun rawOffsetToUserValue(rawOffsetPx: Float, maxWidthPx: Float): Float {
      if (slideSteps != null) {
        val slideStepPx = (maxWidthPx / slideSteps.coerceAtLeast(1).toFloat()).coerceIn(1f, maxWidthPx)
        val userValue = ((rawOffsetPx / slideStepPx).roundToInt() * slideStepPx.roundToInt()).toFloat() / maxWidthPx

        return userValue.coerceIn(0f, 1f)
      }

      return rawOffsetPx / maxWidthPx
    }

    val draggableState = rememberDraggableState(
      onDelta = { delta ->
        rawOffsetInPx = (rawOffsetInPx + delta).coerceIn(0f, maxWidthPx)
        slideOffsetState.value = rawOffsetToUserValue(rawOffsetInPx, maxWidthPx)
        onValueChange(slideOffset)
      }
    )

    val gestureEndAction = rememberUpdatedState<(Float) -> Unit> {
      slideOffsetState.value = rawOffsetToUserValue(rawOffsetInPx, maxWidthPx)
      onValueChange(slideOffset)
    }

    val interactions = remember { mutableStateListOf<Interaction>() }
    LaunchedEffect(interactionSource) {
      interactionSource.interactions.collect { interaction ->
        when (interaction) {
          is PressInteraction.Press -> interactions.add(interaction)
          is PressInteraction.Release -> interactions.remove(interaction.press)
          is PressInteraction.Cancel -> interactions.remove(interaction.press)
          is DragInteraction.Start -> interactions.add(interaction)
          is DragInteraction.Stop -> interactions.remove(interaction.start)
          is DragInteraction.Cancel -> interactions.remove(interaction.start)
        }
      }
    }

    val thumbRadius = if (interactions.isNotEmpty()) {
      thumbRadiusPressedPx
    } else {
      thumbRadiusNormalPx
    }

    val thumbColor = if (interactions.isNotEmpty()) {
      thumbColorNormal
    } else {
      thumbColorPressed
    }

    Canvas(
      modifier = Modifier
        .sliderPressModifier(
          draggableState = draggableState,
          interactionSource = interactionSource,
          maxPx = maxWidthPx,
          isRtl = false,
          rawOffset = rawOffsetState,
          gestureEndAction = gestureEndAction,
          enabled = true
        )
        .draggable(
          state = draggableState,
          orientation = Orientation.Horizontal,
          interactionSource = interactionSource,
          onDragStopped = { velocity -> gestureEndAction.value.invoke(velocity) }
        )
        .then(Modifier.fillMaxSize()),
      onDraw = {
        val centerY = size.height / 2f
        val thumbCenterY = (size.height + trackHeightPx) / 2f
        val halfRadius = thumbRadiusNormalPx / 2

        drawRect(
          color = notLoadedYetPartColor,
          topLeft = Offset(0f, centerY),
          size = Size(size.width, trackHeightPx)
        )

        if (demuxerCachePercents != null) {
          drawRect(
            color = trackColor,
            topLeft = Offset(0f, centerY),
            size = Size(size.width * demuxerCachePercents, trackHeightPx)
          )
        }

        val positionX = (slideOffset * size.width)
          .coerceIn(halfRadius, size.width - halfRadius)

        drawCircle(
          color = thumbColor,
          radius = thumbRadius,
          center = Offset(x = positionX, y = thumbCenterY)
        )
      }
    )
  }
}

private fun Modifier.sliderPressModifier(
  draggableState: DraggableState,
  interactionSource: MutableInteractionSource,
  maxPx: Float,
  isRtl: Boolean,
  rawOffset: State<Float>,
  gestureEndAction: State<(Float) -> Unit>,
  enabled: Boolean
): Modifier {
  if (!enabled) {
    return this
  }

  return pointerInput(draggableState, interactionSource, maxPx, isRtl) {
    detectTapGestures(
      onPress = { pos ->
        draggableState.drag(MutatePriority.UserInput) {
          val to = if (isRtl) maxPx - pos.x else pos.x
          dragBy(to - rawOffset.value)
        }

        val interaction = PressInteraction.Press(pos)
        interactionSource.emit(interaction)

        val finishInteraction = try {
          val success = tryAwaitRelease()
          gestureEndAction.value.invoke(0f)

          if (success) {
            PressInteraction.Release(interaction)
          } else {
            PressInteraction.Cancel(interaction)
          }
        } catch (c: CancellationException) {
          PressInteraction.Cancel(interaction)
        }

        interactionSource.emit(finishInteraction)
      }
    )
  }
}