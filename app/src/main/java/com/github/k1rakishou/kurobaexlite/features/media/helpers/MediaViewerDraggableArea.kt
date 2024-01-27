package com.github.k1rakishou.kurobaexlite.features.media.helpers

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.awaitPointerSlopOrCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.absoluteValue
import android.graphics.Color as AndroidColor

private val flingVelocity = 7000f
private val flingAnimationDuration = 250
private val interpolator = LinearInterpolator()
private val boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)


@Composable
fun MediaViewerDraggableArea(
  availableSize: IntSize,
  closeScreen: () -> Unit,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val androidHelpers = koinRemember<AndroidHelpers>()

  val distToClose = with(density) { 200.dp.toPx() }
  val endActionChannel = remember { Channel<EndAction>(Channel.CONFLATED) }

  val currentPosition = remember { mutableStateOf(Offset(0f, 0f)) }
  val ignoreAllMotionEvents = remember { mutableStateOf(false) }
  val contentAlpha = remember { mutableFloatStateOf(1f) }
  val showCloseViewerLabel = remember { mutableStateOf(false) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      val fps = androidHelpers.getDisplayFps()
      val delayMs = (1000f / fps.toFloat()).toLong().coerceAtLeast(1)

      val scroller = Scroller(context, interpolator)
      scroller.setFriction(0.5f)

      endActionChannel.consumeEach { endAction ->
        try {
          when (endAction) {
            is EndAction.Fling -> {
              val startX = (endAction.currentPosition.x).toInt()
              val startY = (endAction.currentPosition.y).toInt()

              val velocityX = (endAction.velocity.x).toInt()
              val velocityY = (endAction.velocity.y).toInt()

              val minX = Int.MIN_VALUE
              val maxX = Int.MAX_VALUE

              val minY = Int.MIN_VALUE
              val maxY = Int.MAX_VALUE

              scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
            }
            is EndAction.ScrollBack -> {
              val startX = endAction.currentPosition.x.toInt()
              val startY = endAction.currentPosition.y.toInt()

              val deltaX = -startX
              val deltaY = -startY

              scroller.startScroll(startX, startY, deltaX, deltaY, 125)
            }
            is EndAction.Close -> {
              contentAlpha.value = 0f
              closeScreen()
              return@consumeEach
            }
          }

          val endTime = SystemClock.elapsedRealtime() + flingAnimationDuration

          try {
            while (true) {
              ensureActive()

              if (!scroller.computeScrollOffset()) {
                break
              }

              if (endAction is EndAction.Fling) {
                if (scroller.currVelocity < flingVelocity / 4f) {
                  break
                }

                if (SystemClock.elapsedRealtime() > endTime) {
                  break
                }
              }

              currentPosition.value = Offset(scroller.currX.toFloat(), scroller.currY.toFloat())

              if (endAction is EndAction.Fling) {
                contentAlpha.value = calculateAlpha(
                  startX = scroller.startX.toFloat(),
                  startY = scroller.startY.toFloat(),
                  currX = scroller.currX.toFloat(),
                  currY = scroller.currY.toFloat(),
                  finalX = scroller.finalX.toFloat(),
                  finalY = scroller.finalY.toFloat(),
                  invert = endAction is EndAction.ScrollBack
                )
              }

              delay(delayMs)
            }
          } catch (error: Throwable) {
            scroller.abortAnimation()
            currentPosition.value = Offset(scroller.currX.toFloat(), scroller.currY.toFloat())
          }
        } finally {
          ignoreAllMotionEvents.value = false

          if (endAction is EndAction.Fling) {
            contentAlpha.value = 0f
            closeScreen()
          }
        }
      }
    }
  )

  val availableSizeDp = remember(key1 = availableSize) {
    with(density) {
      DpSize(
        width = availableSize.width.toDp(),
        height = availableSize.height.toDp()
      )
    }
  }

  Box(
    modifier = Modifier
      .size(availableSizeDp)
      .pointerInput(
        currentPosition,
        showCloseViewerLabel,
        ignoreAllMotionEvents,
        endActionChannel,
        block = {
          awaitEachGesture {
            val firstChange = awaitFirstDown(requireUnconsumed = false)
            if (ignoreAllMotionEvents.value) {
              return@awaitEachGesture
            }

            var skipGesture = false

            val touchSlopChange = awaitPointerSlopOrCancellation(
              pointerId = firstChange.id,
              pointerType = firstChange.type,
              onPointerSlopReached = { change, overSlop ->
                if (overSlop.y.absoluteValue > overSlop.x.absoluteValue) {
                  change.consume()
                } else {
                  skipGesture = true
                }
              }
            )

            if (touchSlopChange == null || skipGesture) {
              return@awaitEachGesture
            }

            val velocityTracker = VelocityTracker()

            velocityTracker.addPointerInputChange(firstChange)
            firstChange.consume()
            velocityTracker.addPointerInputChange(touchSlopChange)

            val startPosition = touchSlopChange.position
            var currentDist = 0f

            try {
              while (true) {
                val moveEvent = awaitPointerEvent()

                val moveChange = moveEvent.changes.firstOrNull()
                if (moveChange == null || moveChange.changedToUpIgnoreConsumed()) {
                  break
                }

                moveChange.consume()
                velocityTracker.addPointerInputChange(moveChange)

                currentPosition.value = moveChange.position - startPosition
                currentDist = (moveChange.position - startPosition).getDistance()
                showCloseViewerLabel.value = currentDist.absoluteValue > distToClose
              }
            } finally {
              val velocity = velocityTracker.calculateVelocity()
              val velocityHypot = Math
                .hypot(velocity.x.toDouble(), velocity.y.toDouble())
                .toFloat()

              val eventSent = when {
                velocityHypot.absoluteValue > flingVelocity -> {
                  endActionChannel.trySend(EndAction.Fling(currentPosition.value, velocity)).isSuccess
                }

                currentDist > distToClose -> {
                  endActionChannel.trySend(EndAction.Close).isSuccess
                }

                else -> {
                  endActionChannel.trySend(EndAction.ScrollBack(currentPosition.value)).isSuccess
                }
              }

              ignoreAllMotionEvents.value = eventSent
            }
          }
        }
      )
      .absoluteOffset { IntOffset(currentPosition.value.x.toInt(), currentPosition.value.y.toInt()) }
      .graphicsLayer { alpha = contentAlpha.value }
      .overlayModifier(showCloseViewerLabel)
  ) {
    content()
  }
}

@Suppress("AnimateAsStateLabel")
private fun Modifier.overlayModifier(showCloseViewerLabel: State<Boolean>): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val textSize = with(LocalDensity.current) { 30.sp.toPx() }
    val text = stringResource(id = R.string.media_viewer_close_label_text)
    val labelAlpha by animateFloatAsState(targetValue = if (showCloseViewerLabel.value) 1f else 0f)
    val overlayAlpha by animateFloatAsState(targetValue = if (showCloseViewerLabel.value) 0.75f else 0f)

    val textPaint = remember {
      TextPaint().apply {
        this.textSize = textSize
        this.color = chanTheme.accentColor.toArgb()
        this.style = Paint.Style.FILL
        this.typeface = boldTypeface
        this.setShadowLayer(4f, 0f, 0f, AndroidColor.BLACK)
      }
    }

    val textWidth = remember { textPaint.measureText(text) }

    return@composed Modifier.drawWithContent {
      drawContent()

      if (labelAlpha > 0f) {
        drawRect(color = Color.Black.copy(alpha = overlayAlpha))

        val posX = (size.width - textWidth) / 2f
        val poxY = (size.height - textSize) / 2f

        textPaint.alpha = (255f * labelAlpha).toInt()
        drawContext.canvas.nativeCanvas.drawText(text, posX, poxY, textPaint)
      }
    }
  }
}

private fun calculateAlpha(
  startX: Float,
  startY: Float,
  currX: Float,
  currY: Float,
  finalX: Float,
  finalY: Float,
  invert: Boolean
): Float {
  val distToFinish = Math.hypot((currX - finalX).toDouble(), (currY - finalY).toDouble()).absoluteValue
  val totalDist = Math.hypot((startX - finalX).toDouble(), (startY - finalY).toDouble()).absoluteValue

  var alpha = (distToFinish / totalDist).toFloat().coerceIn(0f, 1f)
  if (invert) {
    alpha = 1f - alpha
  }

  return alpha
}

private sealed class EndAction {
  object Close : EndAction()
  data class ScrollBack(val currentPosition: Offset) : EndAction()
  data class Fling(val currentPosition: Offset, val velocity: Velocity) : EndAction()
}