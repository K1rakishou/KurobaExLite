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

suspend fun PointerInputScope.detectDrawerDragGestures(
  drawerLongtapGestureZonePx: Float,
  drawerPhoneVisibleWindowWidthPx: Float,
  drawerWidth: Float,
  pagerSwipeExclusionZone: Rect,
  isDrawerOpened: () -> Boolean,
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
        // TODO(KurobaEx): this starts the Drawer drag as soon as the user clicks inside of the
        //  drawerSpecialGestureZone which makes it impossible to scroll the LazyColumn which is inside
        //  of the Pager. I have no idea how to intercept touch events first then wait for touch slop
        //  and then if the user moved the pointer vertically more than horizontally pass all the
        //  consumed events into the LazyColumn right now. Maybe I should use InteractionSource or maybe there
        //  is something else for that?
        if (pagerSwipeExclusionZone.contains(downEvent.position)) {
          for (change in firstEvent.changes) {
            change.consumeAllChanges()
          }

          val dragProgress = (downEvent.position.x / drawerWidth).coerceIn(0f, 1f)
          prevDragProgress = dragProgress

          onDraggingDrawer(true, dragProgress, 0f)
        } else {
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