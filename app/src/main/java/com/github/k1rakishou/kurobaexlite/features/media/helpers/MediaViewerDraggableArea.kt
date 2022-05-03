package com.github.k1rakishou.kurobaexlite.features.media.helpers

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import kotlin.math.absoluteValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

private val flingVelocity = 7000f
private val flingAnimationDuration = 250
private val interpolator = LinearInterpolator()
private val boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DraggableArea(
  closeScreen: () -> Unit,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val androidHelpers = koinRemember<AndroidHelpers>()

  val distToClose = with(density) { 200.dp.toPx() }
  val endActionChannel = remember { Channel<EndAction>(Channel.RENDEZVOUS) }

  var currentPosition by remember { mutableStateOf(Offset(0f, 0f)) }
  var ignoreAllMotionEvents by remember { mutableStateOf(false) }
  var contentAlpha by remember { mutableStateOf(1f) }
  var showCloseViewerLabel by remember { mutableStateOf(false) }

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
              closeScreen()
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

              currentPosition = Offset(scroller.currX.toFloat(), scroller.currY.toFloat())

              contentAlpha = calculateAlpha(
                startX = scroller.startX.toFloat(),
                startY = scroller.startY.toFloat(),
                currX = scroller.currX.toFloat(),
                currY = scroller.currY.toFloat(),
                finalX = scroller.finalX.toFloat(),
                finalY = scroller.finalY.toFloat(),
                invert = endAction is EndAction.ScrollBack
              )

              delay(delayMs)
            }
          } catch (error: Throwable) {
            scroller.abortAnimation()
            currentPosition = Offset(scroller.currX.toFloat(), scroller.currY.toFloat())
          }
        } finally {
          ignoreAllMotionEvents = false

          if (endAction is EndAction.Fling) {
            closeScreen()
          }
        }
      }
    }
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .pointerInput(
        key1 = Unit,
        block = {
          val velocityTracker = VelocityTracker()

          forEachGesture {
            awaitPointerEventScope {
              velocityTracker.resetTracking()

              val downEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
              if (ignoreAllMotionEvents) {
                return@awaitPointerEventScope
              }

              val firstChange = downEvent.changes.firstOrNull()
              if (firstChange == null) {
                return@awaitPointerEventScope
              }

              val touchSlopChange = awaitTouchSlopOrCancellation(
                pointerId = firstChange.id,
                onTouchSlopReached = { change, overSlop ->
                  if (overSlop.y.absoluteValue > overSlop.x.absoluteValue) {
                    change.consumeAllChanges()
                  }
                }
              )

              if (touchSlopChange == null) {
                return@awaitPointerEventScope
              }

              downEvent.changes.fastForEach { pointerInputChange ->
                velocityTracker.addPointerInputChange(pointerInputChange)
                pointerInputChange.consumeAllChanges()
              }

              velocityTracker.addPointerInputChange(touchSlopChange)

              val startPosition = touchSlopChange.position
              var currentDist = 0f

              try {
                while (true) {
                  val moveEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
                  val moveChange = moveEvent.changes.firstOrNull()

                  if (moveChange == null || moveChange.changedToUpIgnoreConsumed()) {
                    break
                  }

                  moveChange.consumeAllChanges()
                  velocityTracker.addPointerInputChange(moveChange)

                  currentPosition = moveChange.position - startPosition
                  currentDist = (moveChange.position - startPosition).getDistance()
                  showCloseViewerLabel = currentDist.absoluteValue > distToClose
                }
              } finally {
                val velocity = velocityTracker.calculateVelocity()
                val velocityHypot = Math
                  .hypot(velocity.x.toDouble(), velocity.y.toDouble())
                  .toFloat()

                when {
                  velocityHypot.absoluteValue > flingVelocity -> {
                    endActionChannel.trySend(EndAction.Fling(currentPosition, velocity))
                  }
                  currentDist > distToClose -> {
                    endActionChannel.trySend(EndAction.Close)
                  }
                  else -> {
                    endActionChannel.trySend(EndAction.ScrollBack(currentPosition))
                  }
                }

                ignoreAllMotionEvents = true
              }
            }
          }
        }
      )
      .absoluteOffset { IntOffset(currentPosition.x.toInt(), currentPosition.y.toInt()) }
      .graphicsLayer { alpha = contentAlpha }
      .composed {
        val chanTheme = LocalChanTheme.current
        val textSize = with(LocalDensity.current) { 30.sp.toPx() }
        val text = stringResource(id = R.string.media_viewer_close_label_text)
        val labelAlpha by animateFloatAsState(targetValue = if (showCloseViewerLabel) 1f else 0f)

        val textPaint = remember {
          TextPaint().apply {
            this.textSize = textSize
            this.color = chanTheme.accentColor
            this.style = Paint.Style.FILL
            this.typeface = boldTypeface
            this.setShadowLayer(4f, 0f, 0f, Color.BLACK)
          }
        }

        val textWidth = remember { textPaint.measureText(text) }

        return@composed Modifier.drawWithContent {
          drawContent()

          if (labelAlpha > 0f) {
            val posX = (size.width - textWidth) / 2f
            val poxY = (size.height - textSize) / 2f

            textPaint.alpha = (255f * labelAlpha).toInt()
            drawContext.canvas.nativeCanvas.drawText(text, posX, poxY, textPaint)
          }
        }
      }
  ) {
    content()
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