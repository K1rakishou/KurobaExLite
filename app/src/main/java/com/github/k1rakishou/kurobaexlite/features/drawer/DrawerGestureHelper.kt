package com.github.k1rakishou.kurobaexlite.features.drawer

import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChangeConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.absoluteValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

suspend fun PointerInputScope.detectDrawerDragGestures(
  drawerLongtapGestureZonePx: Float,
  drawerPhoneVisibleWindowWidthPx: Float,
  drawerWidth: Float,
  pagerSwipeExclusionZone: Rect,
  isDrawerOpened: () -> Boolean,
  onStopConsumingScrollEvents: () -> Unit,
  isGestureCurrentlyAllowed: () -> Boolean,
  onDraggingDrawer: (Boolean, Float, Float) -> Unit
) {
  val velocityTracker = VelocityTracker()

  coroutineScope {
    forEachGesture {
      var prevDragProgress = 0f
      var dragDownEvent: PointerInputChange? = null

      val firstEvent = awaitPointerEventScope {
        val firstEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
        if (firstEvent.type != PointerEventType.Press) {
          return@awaitPointerEventScope null
        }

        if (drawerWidth <= 0 || pagerSwipeExclusionZone.size.isEmpty()) {
          return@awaitPointerEventScope null
        }

        if (!isGestureCurrentlyAllowed()) {
          return@awaitPointerEventScope null
        }

        val downEvent = firstEvent.changes.firstOrNull()
          ?: return@awaitPointerEventScope null
        dragDownEvent = downEvent

        if (isDrawerOpened()) {
          var overSlop = Offset.Zero
          onStopConsumingScrollEvents()

          val touchSlopChange = awaitTouchSlopOrCancellation(downEvent.id) { change, slop ->
            change.consumeAllChanges()
            overSlop = slop
          }

          if (touchSlopChange == null || overSlop.y.absoluteValue > overSlop.x.absoluteValue) {
            return@awaitPointerEventScope null
          }

          if (downEvent.position.x > (drawerWidth - drawerPhoneVisibleWindowWidthPx)) {
            return@awaitPointerEventScope null
          }
        } else {
          if (pagerSwipeExclusionZone.contains(downEvent.position)) {
            var isDrawerDragEvent = false
            var firstEventConsumed = false

            val firstEventTotalTravelDistance = firstEvent.changes
              .fold(Offset.Zero) { acc, change -> acc + change.position }

            while (isActive) {
              val nextEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
              if (nextEvent.type != PointerEventType.Move) {
                break
              }

              if (!firstEventConsumed) {
                firstEventConsumed = true

                for (change in firstEvent.changes) {
                  change.consumeAllChanges()
                }
              }

              val nextEventTotalTravelDistance = nextEvent.changes
                .fold(Offset.Zero) { acc, change -> acc + change.position }

              val distanceDelta = nextEventTotalTravelDistance - firstEventTotalTravelDistance

              if (distanceDelta.y.absoluteValue > distanceDelta.x.absoluteValue || distanceDelta.x < 0) {
                break
              }

              if (distanceDelta.x > viewConfiguration.touchSlop) {
                isDrawerDragEvent = true
                break
              }

              for (change in nextEvent.changes) {
                change.consumeAllChanges()
              }
            }

            if (!isDrawerDragEvent) {
              onStopConsumingScrollEvents()
              return@awaitPointerEventScope null
            }

            val dragProgress = (downEvent.position.x / drawerWidth).coerceIn(0f, 1f)
            prevDragProgress = dragProgress

            onDraggingDrawer(true, dragProgress, 0f)
          } else {
            onStopConsumingScrollEvents()

            if (downEvent.position.x > drawerLongtapGestureZonePx) {
              return@awaitPointerEventScope null
            }

            for (change in firstEvent.changes) {
              change.consumeAllChanges()
            }

            val longPress = kurobaAwaitLongPressOrCancellation(
              initialDown = downEvent,
              isActive = { isActive }
            )

            if (longPress == null || longPress.position.x > drawerLongtapGestureZonePx) {
              return@awaitPointerEventScope null
            } else {
              val dragProgress = (longPress.position.x / drawerWidth).coerceIn(0f, 1f)
              prevDragProgress = dragProgress

              onDraggingDrawer(true, dragProgress, 0f)
            }
          }
        }

        return@awaitPointerEventScope firstEvent
      }

      if (firstEvent == null || dragDownEvent == null) {
        return@forEachGesture
      }

      try {
        for (change in firstEvent.changes) {
          velocityTracker.addPointerInputChange(change)
          change.consumeAllChanges()
        }

        awaitPointerEventScope {
          while (isActive) {
            val moveEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
            for (change in moveEvent.changes) {
              change.consumeAllChanges()
            }

            val drag = moveEvent.changes.firstOrNull { it.id == dragDownEvent!!.id }
              ?: break

            if (drag.changedToUpIgnoreConsumed()) {
              break
            }

            velocityTracker.addPointerInputChange(drag)

            val dragProgress = (drag.position.x / drawerWidth).coerceIn(0f, 1f)
            prevDragProgress = dragProgress

            onDraggingDrawer(true, dragProgress, 0f)
          }
        }
      } finally {
        val velocityX = velocityTracker.calculateVelocity().x

        onDraggingDrawer(false, prevDragProgress, velocityX)
        velocityTracker.resetTracking()
      }
    }
  }
}

private suspend fun AwaitPointerEventScope.kurobaAwaitLongPressOrCancellation(
  initialDown: PointerInputChange,
  isActive: () -> Boolean
): PointerInputChange? {
  var longPress: PointerInputChange? = null
  var currentDown = initialDown
  val longPressTimeout = viewConfiguration.longPressTimeoutMillis

  try {
    // wait for first tap up or long press
    withTimeout(longPressTimeout) {
      var finished = false
      while (!finished && isActive()) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.fastAll { it.changedToUpIgnoreConsumed() }) {
          // All pointers are up
          finished = true
        }

        if (
          event.changes.fastAny {
            it.consumed.downChange || it.isOutOfBounds(size, extendedTouchPadding)
          }
        ) {
          finished = true // Canceled
        }

        // Check for cancel by position consumption. We can look on the Final pass of
        // the existing pointer event because it comes after the Main pass we checked
        // above.
        val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
        if (consumeCheck.changes.fastAny { it.positionChangeConsumed() }) {
          finished = true
        }
        if (!event.isPointerUp(currentDown.id)) {
          longPress = event.changes.fastFirstOrNull { it.id == currentDown.id }
        } else {
          val newPressed = event.changes.fastFirstOrNull { it.pressed }
          if (newPressed != null) {
            currentDown = newPressed
            longPress = currentDown
          } else {
            // should technically never happen as we checked it above
            finished = true
          }
        }
      }
    }

    return null
  } catch (_: PointerEventTimeoutCancellationException) {
    return longPress ?: initialDown
  }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
  changes.fastFirstOrNull { it.id == pointerId }?.pressed != true