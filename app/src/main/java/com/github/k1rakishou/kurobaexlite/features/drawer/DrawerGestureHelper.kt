package com.github.k1rakishou.kurobaexlite.features.drawer

import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlin.math.absoluteValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

@OptIn(ExperimentalComposeUiApi::class)
suspend fun PointerInputScope.detectDrawerDragGestures(
  drawerLongtapGestureWidthZonePx: Float,
  drawerPhoneVisibleWindowWidthPx: Float,
  drawerWidth: Float,
  pagerSwipeExclusionZone: Rect,
  isDrawerOpened: () -> Boolean,
  onStopConsumingScrollEvents: () -> Unit,
  isGestureCurrentlyAllowed: () -> Boolean,
  onLongtapDragGestureDetected: () -> Unit,
  onFailedDrawerDragGestureDetected: () -> Unit,
  onDraggingDrawer: (dragging: Boolean, current: Float) -> Unit
) {
  coroutineScope {
    forEachGesture {
      var prevDragPositionX = 0f
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
            for (change in firstEvent.changes) {
              change.consumeAllChanges()
            }

            var isDrawerDragEvent = false

            val firstEventTotalTravelDistance = firstEvent.changes
              .fold(Offset.Zero) { acc, change -> acc + change.position }

            val collectedEvents = mutableListOf<PointerEvent>()

            while (isActive) {
              val nextEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
              if (nextEvent.type != PointerEventType.Move) {
                break
              }

              val nextEventTotalTravelDistance = nextEvent.changes
                .fold(Offset.Zero) { acc, change -> acc + change.position }

              val distanceDelta = nextEventTotalTravelDistance - firstEventTotalTravelDistance

              if (distanceDelta.y.absoluteValue > distanceDelta.x.absoluteValue || distanceDelta.x < 0) {
                break
              }

              for (change in nextEvent.changes) {
                change.consumeAllChanges()
              }

              collectedEvents += nextEvent

              if (distanceDelta.x > viewConfiguration.touchSlop) {
                isDrawerDragEvent = true
                break
              }
            }

            if (!isDrawerDragEvent) {
              onFailedDrawerDragGestureDetected()
              onStopConsumingScrollEvents()
              return@awaitPointerEventScope null
            }

            prevDragPositionX = downEvent.position.x

            downEvent.historical.fastForEach { historicalChange ->
              onDraggingDrawer(true, historicalChange.position.x)
            }
            collectedEvents.fastForEach { pointerEvent ->
              pointerEvent.changes.fastForEach { pointerInputChange ->
                pointerInputChange.historical.forEach { historicalChange ->
                  onDraggingDrawer(true, historicalChange.position.x)
                }
                onDraggingDrawer(true, pointerInputChange.position.x)
              }
            }

            onDraggingDrawer(true, downEvent.position.x)
          } else {
            onStopConsumingScrollEvents()

            if (downEvent.position.x > drawerLongtapGestureWidthZonePx) {
              return@awaitPointerEventScope null
            }

            for (change in firstEvent.changes) {
              change.consumeAllChanges()
            }

            val longPress = kurobaAwaitLongPressOrCancellation(
              initialDown = downEvent,
              isActive = { isActive }
            )

            if (longPress == null || longPress.position.x > drawerLongtapGestureWidthZonePx) {
              return@awaitPointerEventScope null
            }

            onLongtapDragGestureDetected()
            prevDragPositionX = downEvent.position.x

            downEvent.historical.fastForEach { historicalChange ->
              onDraggingDrawer(true, historicalChange.position.x)
            }
            onDraggingDrawer(true, downEvent.position.x)
          }
        }

        return@awaitPointerEventScope firstEvent
      }

      if (firstEvent == null || dragDownEvent == null) {
        return@forEachGesture
      }

      try {
        for (change in firstEvent.changes) {
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

            prevDragPositionX = drag.position.x

            drag.historical.fastForEach { historicalChange ->
              onDraggingDrawer(true, historicalChange.position.x)
            }
            onDraggingDrawer(true, drag.position.x)
          }
        }
      } finally {
        onDraggingDrawer(false, prevDragPositionX)
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