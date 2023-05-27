package com.github.k1rakishou.zoomable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.filter

/**
 * A _drop-in_ replacement for async `Image()` composables featuring support for pan & zoom gestures
 * and automatic sub-sampling of large images. This ensures that images maintain their intricate details
 * even when fully zoomed in, without causing any `OutOfMemory` exceptions.
 *
 * Because `Modifier.zoomable()` consumes all gestures including double-taps, [Modifier.clickable]
 * and [Modifier.combinedClickable] will not work on this composable. As an alternative, [onClick]
 * and [onLongClick] parameters can be used instead.
 *
 * @param gesturesEnabled whether or not gestures are enabled.
 *
 * @param clipToBounds defaults to true to act as a reminder that this layout should probably fill all
 * available space. Otherwise, gestures made outside the composable's layout bounds will not be registered.
 */
@Composable
fun ZoomableImage(
  image: ZoomableImageSource,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  gesturesEnabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
) {
  state.zoomableState.also {
    it.contentAlignment = alignment
    it.contentScale = contentScale
  }

  var canvasSize by remember { mutableStateOf(Size.Unspecified) }
  val resolved = key(image) {
    image.resolve(
      canvasSize = remember {
        snapshotFlow { canvasSize }.filter { it.isSpecified }
      }
    )
  }

  Box(
    modifier = modifier.onMeasure { canvasSize = it },
    propagateMinConstraints = true,
  ) {
    state.isImageDisplayed = when (resolved.delegate) {
      is ZoomableImageSource.PainterDelegate -> resolved.delegate.painter != null
      is ZoomableImageSource.SubSamplingDelegate -> state.subSamplingState?.isImageLoaded ?: false
      else -> false
    }
    val animatedAlpha by animateFloatAsState(
      targetValue = if (state.isImageDisplayed) 1f else 0f,
      animationSpec = tween(resolved.crossfadeDurationMs)
    )

    state.isPlaceholderDisplayed = resolved.placeholder != null && animatedAlpha < 1f
    if (state.isPlaceholderDisplayed) {
      Image(
        painter = animatedPainter(resolved.placeholder!!).scaledToMatch(
          // Align with the full-quality image even if the placeholder is smaller in size.
          // This will only work when ZoomableImage is given fillMaxSize or a fixed size.
          state.zoomableState.contentTransformation.contentSize,
        ),
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }

    val zoomable = Modifier.zoomable(
      state = state.zoomableState,
      enabled = gesturesEnabled && !state.isPlaceholderDisplayed,
      onClick = onClick,
      onLongClick = onLongClick,
      clipToBounds = clipToBounds,
    )
    when (val delegate = resolved.delegate) {
      null -> {
        Box(modifier)
      }

      is ZoomableImageSource.PainterDelegate -> {
        val painter = delegate.painter ?: EmptyPainter
        LaunchedEffect(painter.intrinsicSize) {
          state.zoomableState.setContentLocation(
            ZoomableContentLocation.scaledInsideAndCenterAligned(painter.intrinsicSize)
          )
        }
        Image(
          modifier = zoomable,
          painter = animatedPainter(painter),
          contentDescription = contentDescription,
          alignment = Alignment.Center,
          contentScale = ContentScale.Inside,
          alpha = alpha * animatedAlpha,
          colorFilter = colorFilter,
        )
      }

      is ZoomableImageSource.SubSamplingDelegate -> {
        val subSamplingState = rememberSubSamplingImageState(
          imageSource = delegate.source,
          zoomableState = state.zoomableState,
          imageOptions = delegate.imageOptions
        )
        DisposableEffect(subSamplingState) {
          state.subSamplingState = subSamplingState
          onDispose {
            state.subSamplingState = null
          }
        }
        LaunchedEffect(subSamplingState.imageSize) {
          state.zoomableState.setContentLocation(
            ZoomableContentLocation.unscaledAndTopStartAligned(
              subSamplingState.imageSize?.toSize()
            )
          )
        }
        SubSamplingImage(
          modifier = zoomable,
          state = subSamplingState,
          contentDescription = contentDescription,
          alpha = alpha * animatedAlpha,
          colorFilter = colorFilter,
        )
      }
    }
  }
}

private fun Modifier.onMeasure(action: (Size) -> Unit): Modifier {
  return layout { measurable, constraints ->
    val maxSize = when {
      constraints.isZero -> Size.Unspecified
      else -> Size(
        width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.NaN,
        height = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.NaN
      )
    }
    action(maxSize)

    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
      placeable.place(0, 0)
    }
  }
}

@Composable
private fun animatedPainter(painter: Painter): Painter {
  if (painter is RememberObserver) {
    // Animated painters use RememberObserver's APIs
    // for starting & stopping their animations.
    return remember(painter) { painter }
  }
  return painter
}

private object EmptyPainter : Painter() {
  override val intrinsicSize: Size get() = Size.Unspecified
  override fun DrawScope.onDraw() = Unit
}

private val ZoomableImageSource.ResolveResult.crossfadeDurationMs: Int
  get() = crossfadeDuration.inWholeMilliseconds.toInt()
