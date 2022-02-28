package com.github.k1rakishou.kurobaexlite.ui.screens.drawer

import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import com.github.k1rakishou.kurobaexlite.helpers.kurobaAwaitLongPressOrCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlin.math.absoluteValue

suspend fun PointerInputScope.detectDrawerDragGestures(
  drawerLongtapGestureZonePx: Float,
  drawerPhoneVisibleWindowWidthPx: Float,
  drawerWidth: Float,
  pagerSwipeExclusionZone: Rect,
  isDrawerOpened: () -> Boolean,
  onStopConsumingScrollEvents: () -> Unit,
  onDraggingDrawer: (Boolean, Float, Float) -> Unit
) {
  val velocityTracker = VelocityTracker()

  coroutineScope {
    forEachGesture {
      val firstEvent = awaitPointerEventScope { awaitPointerEvent(pass = PointerEventPass.Initial) }
      if (firstEvent.type != PointerEventType.Press) {
        return@forEachGesture
      }

      if (drawerWidth <= 0 || pagerSwipeExclusionZone.size.isEmpty()) {
        return@forEachGesture
      }

      val downEvent = firstEvent.changes.firstOrNull()
        ?: return@forEachGesture

      var prevDragProgress = 0f

      if (isDrawerOpened()) {
        var overSlop = Offset.Zero
        onStopConsumingScrollEvents()

        val touchSlopChange = awaitPointerEventScope {
          awaitTouchSlopOrCancellation(downEvent.id) { change, slop ->
            change.consumeAllChanges()
            overSlop = slop
          }
        }

        if (touchSlopChange == null || overSlop.y.absoluteValue > overSlop.x.absoluteValue) {
          return@forEachGesture
        }

        if (downEvent.position.x > (drawerWidth - drawerPhoneVisibleWindowWidthPx)) {
          return@forEachGesture
        }
      } else {
        if (pagerSwipeExclusionZone.contains(downEvent.position)) {
          for (change in firstEvent.changes) {
            change.consumeAllChanges()
          }

          var isDrawerDragEvent = false

          val firstEventTotalTravelDistance = firstEvent.changes
            .fold(Offset.Zero) { acc, change -> acc + change.position }

          awaitPointerEventScope {
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

              if (distanceDelta.x > viewConfiguration.touchSlop) {
                isDrawerDragEvent = true
                break
              }

              for (change in nextEvent.changes) {
                change.consumeAllChanges()
              }
            }
          }

          if (!isDrawerDragEvent) {
            onStopConsumingScrollEvents()
            return@forEachGesture
          }

          val dragProgress = (downEvent.position.x / drawerWidth).coerceIn(0f, 1f)
          prevDragProgress = dragProgress

          onDraggingDrawer(true, dragProgress, 0f)
        } else {
          onStopConsumingScrollEvents()

          val longPress = kurobaAwaitLongPressOrCancellation(downEvent)
          if (longPress == null || longPress.position.x > (drawerLongtapGestureZonePx)) {
            return@forEachGesture
          } else {
            val dragProgress = (longPress.position.x / drawerWidth).coerceIn(0f, 1f)
            prevDragProgress = dragProgress

            onDraggingDrawer(true, dragProgress, 0f)
          }
        }
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

            val drag = moveEvent.changes.firstOrNull { it.id == downEvent.id }
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