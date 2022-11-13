package com.github.k1rakishou.kurobaexlite.features.drawer

import android.os.SystemClock
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.ui.helpers.gesture.awaitPointerSlopOrCancellationWithPass
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.math.absoluteValue

@OptIn(ExperimentalComposeUiApi::class)
suspend fun PointerInputScope.detectDrawerDragGestures(
  drawerLongtapGestureWidthZonePx: Float,
  drawerWidth: Float,
  mainUiLayoutMode: MainUiLayoutMode,
  isDrawerOpened: () -> Boolean,
  onStopConsumingScrollEvents: () -> Unit,
  isGestureCurrentlyAllowed: () -> Boolean,
  onLongtapDragGestureDetected: () -> Unit,
  onDraggingDrawer: (dragging: Boolean, time: Long, current: Float) -> Unit
) {
  coroutineScope {
    forEachGesture {
      var prevDragPositionX = 0f
      var prevTime = SystemClock.elapsedRealtime()
      var dragDownEvent: PointerInputChange? = null

      val firstEvent = awaitPointerEventScope {
        val firstEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
        if (firstEvent.type != PointerEventType.Press) {
          return@awaitPointerEventScope null
        }

        if (drawerWidth <= 0) {
          return@awaitPointerEventScope null
        }

        val downEvent = firstEvent.changes.firstOrNull()
          ?: return@awaitPointerEventScope null

        if (!isGestureCurrentlyAllowed()) {
          return@awaitPointerEventScope null
        }

        dragDownEvent = downEvent

        if (isDrawerOpened()) {
          var overSlop: Offset? = null
          onStopConsumingScrollEvents()

          val touchSlopChange = awaitPointerSlopOrCancellationWithPass(
            pointerId = downEvent.id,
            pointerEventPass = PointerEventPass.Initial
          ) { change, slop ->
            // The distance between end and start must be moving horizontally more than vertically
            // because we are waiting for horizontal drags.
            if (slop.x.absoluteValue > slop.y.absoluteValue) {
              change.consume()
              overSlop = slop
            }
          }

          if (touchSlopChange == null || overSlop == null) {
            return@awaitPointerEventScope null
          }

          if (downEvent.position.x > drawerWidth) {
            return@awaitPointerEventScope null
          }
        } else if (mainUiLayoutMode != MainUiLayoutMode.Split) {
          onStopConsumingScrollEvents()

          if (downEvent.position.x > drawerLongtapGestureWidthZonePx) {
            return@awaitPointerEventScope null
          }

          for (change in firstEvent.changes) {
            change.consume()
          }

          val longPress = kurobaAwaitLongPressOrCancellation(
            timeout = viewConfiguration.longPressTimeoutMillis / 2,
            initialDown = downEvent,
            isActive = { isActive }
          )

          if (longPress == null || longPress.position.x > drawerLongtapGestureWidthZonePx) {
            return@awaitPointerEventScope null
          }

          onLongtapDragGestureDetected()
          prevDragPositionX = downEvent.position.x
          prevTime = downEvent.uptimeMillis

          downEvent.historical.fastForEach { historicalChange ->
            onDraggingDrawer(true, historicalChange.uptimeMillis, historicalChange.position.x)
          }
          onDraggingDrawer(true, downEvent.uptimeMillis, downEvent.position.x)
        }

        return@awaitPointerEventScope firstEvent
      }

      if (firstEvent == null || dragDownEvent == null) {
        return@forEachGesture
      }

      try {
        for (change in firstEvent.changes) {
          change.consume()
        }

        awaitPointerEventScope {
          while (isActive) {
            val moveEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
            for (change in moveEvent.changes) {
              change.consume()
            }

            val drag = moveEvent.changes.firstOrNull { it.id == dragDownEvent!!.id }
              ?: break

            if (drag.changedToUpIgnoreConsumed()) {
              break
            }

            prevDragPositionX = drag.position.x
            prevTime = drag.uptimeMillis

            drag.historical.fastForEach { historicalChange ->
              onDraggingDrawer(true, historicalChange.uptimeMillis, historicalChange.position.x)
            }
            onDraggingDrawer(true, drag.uptimeMillis, drag.position.x)
          }
        }
      } finally {
        onDraggingDrawer(false, prevTime, prevDragPositionX)
      }
    }
  }
}

@Composable
fun rememberDrawerDragGestureDetectorState(): DrawerDragGestureDetectorState {
  return remember { DrawerDragGestureDetectorState() }
}

@Stable
class DrawerDragGestureDetectorState {

  private val _consumeAllScrollEventsState = MutableStateFlow<Boolean>(false)
  val consumeAllScrollEventsState: StateFlow<Boolean>
    get() = _consumeAllScrollEventsState.asStateFlow()

  fun consumeAllScrollEvents(stop: Boolean) {
    _consumeAllScrollEventsState.tryEmit(stop)
  }

}

private suspend fun AwaitPointerEventScope.kurobaAwaitLongPressOrCancellation(
  timeout: Long? = null,
  initialDown: PointerInputChange,
  isActive: () -> Boolean
): PointerInputChange? {
  var longPress: PointerInputChange? = null
  var currentDown = initialDown
  val longPressTimeout = timeout ?: viewConfiguration.longPressTimeoutMillis

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
        if (consumeCheck.changes.fastAny { it.isConsumed }) {
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