package com.github.k1rakishou.kurobaexlite.ui.helpers

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.util.quantize
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine
import kotlinx.coroutines.CancellationException

@Composable
fun KurobaComposeSnappingSlider(
  modifier: Modifier = Modifier,
  slideOffsetState: MutableState<Float>,
  onValueChange: (Float) -> Unit,
  sliderSteps: Int? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
  if (sliderSteps != null) {
    require(sliderSteps > 0) { "sliderSteps must be greater than zero!" }
  }

  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current
  val thumbRadiusNormal = with(density) { 12.dp.toPx() }
  val thumbRadiusPressed = with(density) { 16.dp.toPx() }
  val trackWidth = with(density) { 3.dp.toPx() }
  val slideOffset by slideOffsetState

  BoxWithConstraints(
    modifier = modifier
      .then(
        Modifier
          .widthIn(min = 144.dp)
          .height(42.dp)
      )
  ) {
    val trackColor = chanTheme.accentColor

    val thumbColorNormal = remember(key1 = trackColor) {
      ThemeEngine.resolveDarkOrLightColor(trackColor)
    }

    val thumbColorPressed = remember(key1 = thumbColorNormal) {
      if (ThemeEngine.isDarkColor(thumbColorNormal)) {
        ThemeEngine.manipulateColor(thumbColorNormal, 1.2f)
      } else {
        ThemeEngine.manipulateColor(thumbColorNormal, 0.8f)
      }
    }

    val rawOffsetState = remember { mutableFloatStateOf(0f) }
    var rawOffsetInPx by rawOffsetState

    val draggableState = rememberDraggableState(
      onDelta = { delta ->
        val maxWidthPx = constraints.maxWidth.toFloat()

        rawOffsetInPx = (rawOffsetInPx + delta).coerceIn(0f, maxWidthPx)
        slideOffsetState.value = rawOffsetToUserValue(sliderSteps, rawOffsetInPx, maxWidthPx)
        onValueChange(slideOffset)
      }
    )

    val gestureEndAction = rememberUpdatedState<(Float) -> Unit> {
      val maxWidthPx = constraints.maxWidth.toFloat()

      slideOffsetState.value = rawOffsetToUserValue(sliderSteps, rawOffsetInPx, maxWidthPx)
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
      thumbRadiusPressed
    } else {
      thumbRadiusNormal
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
          maxPx = constraints.maxWidth.toFloat(),
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
        val thumbCenterY = (size.height + trackWidth) / 2f
        val halfRadius = thumbRadiusNormal / 2

        drawRect(
          color = trackColor,
          topLeft = Offset(0f, centerY),
          size = Size(size.width, trackWidth)
        )

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

private fun rawOffsetToUserValue(sliderSteps: Int?, rawOffsetPx: Float, maxWidthPx: Float): Float {
  if (sliderSteps == null) {
    return rawOffsetPx / maxWidthPx
  }

  return (rawOffsetPx / maxWidthPx)
    .quantize(precision = 1f / sliderSteps)
    .coerceIn(0f, 1f)
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
