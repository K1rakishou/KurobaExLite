package com.github.k1rakishou.kurobaexlite.ui.screens.drawer

import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import com.github.k1rakishou.kurobaexlite.helpers.kurobaAwaitLongPressOrCancellation

suspend fun PointerInputScope.detectDrawerDragGestures(
  drawerLongtapGestureZonePx: Float,
  drawerPhoneVisibleWindowWidthPx: Float,
  drawerWidth: Float,
  isDrawerOpened: () -> Boolean,
  onDraggingDrawer: (Boolean, Float, Float) -> Unit
) {
  val velocityTracker = VelocityTracker()

  forEachGesture {
    val firstEvent = awaitPointerEventScope { awaitPointerEvent(pass = PointerEventPass.Initial) }
    if (firstEvent.type != PointerEventType.Press) {
      return@forEachGesture
    }

    if (drawerWidth <= 0) {
      return@forEachGesture
    }

    val downEvent = firstEvent.changes.firstOrNull()
      ?: return@forEachGesture

    var prevDragProgress = 0f

    if (isDrawerOpened()) {
      var overSlop = Offset.Zero

      val touchSlopChange = awaitPointerEventScope {
        awaitTouchSlopOrCancellation(downEvent.id) { change, slop ->
          change.consumeAllChanges()
          overSlop = slop
        }
      }

      if (touchSlopChange == null || Math.abs(overSlop.y) > Math.abs(overSlop.x)) {
        return@forEachGesture
      }

      if (downEvent.position.x > (drawerWidth - drawerPhoneVisibleWindowWidthPx)) {
        return@forEachGesture
      }
    } else {
      val longPress = kurobaAwaitLongPressOrCancellation(downEvent)
      if (longPress == null || longPress.position.x > (drawerLongtapGestureZonePx)) {
        return@forEachGesture
      }

      val dragProgress = (longPress.position.x / drawerWidth).coerceIn(0f, 1f)
      prevDragProgress = dragProgress

      onDraggingDrawer(true, dragProgress, 0f)
    }

    try {
      awaitPointerEventScope {
        for (change in firstEvent.changes) {
          velocityTracker.addPointerInputChange(change)
          change.consumeAllChanges()
        }

        while (true) {
          val moveEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
          val drag = moveEvent.changes.firstOrNull { it.id == downEvent.id }
            ?: break

          if (drag.changedToUpIgnoreConsumed()) {
            break
          }

          velocityTracker.addPointerInputChange(drag)

          val dragProgress = (drag.position.x / drawerWidth).coerceIn(0f, 1f)
          prevDragProgress = dragProgress

          onDraggingDrawer(true, dragProgress, 0f)

          for (change in moveEvent.changes) {
            change.consumeAllChanges()
          }
        }
      }
    } finally {
      val velocityX = velocityTracker.calculateVelocity().x

      onDraggingDrawer(false, prevDragProgress, velocityX)
      velocityTracker.resetTracking()
    }
  }
}