package com.github.k1rakishou.kurobaexlite.helpers

import androidx.compose.foundation.gestures.GestureCancellationException
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChangeConsumed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

private val NoPressGesture: suspend PressGestureScope.(Offset) -> Unit = { }


suspend fun PointerInputScope.detectTapGesturesWithFilter(
  processDownEvent: (Offset) -> Boolean,
  onUpOrCancel: (() -> Unit)? = null,
  onDoubleTap: ((Offset) -> Unit)? = null,
  onLongPress: ((Offset) -> Unit)? = null,
  onPress: suspend PressGestureScope.(Offset) -> Unit = NoPressGesture,
  onTap: ((Offset) -> Unit)? = null
) = coroutineScope {
  // special signal to indicate to the sending side that it shouldn't intercept and consume
  // cancel/up events as we're only require down events
  val pressScope = PressGestureScopeImpl(this@detectTapGesturesWithFilter)

  forEachGesture {
    awaitPointerEventScope {
      val down = awaitFirstDown()

      if (!processDownEvent(down.position)) {
        return@awaitPointerEventScope
      }

      down.consumeDownChange()
      pressScope.reset()

      if (onPress !== NoPressGesture) launch {
        pressScope.onPress(down.position)
      }
      val longPressTimeout = onLongPress?.let {
        viewConfiguration.longPressTimeoutMillis
      } ?: (Long.MAX_VALUE / 2)
      var upOrCancel: PointerInputChange? = null
      try {
        // wait for first tap up or long press
        upOrCancel = withTimeout(longPressTimeout) {
          waitForUpOrCancellation()
        }
        if (upOrCancel == null) {
          pressScope.cancel() // tap-up was canceled
        } else {
          upOrCancel.consumeDownChange()
          pressScope.release()
        }
      } catch (_: PointerEventTimeoutCancellationException) {
        onLongPress?.invoke(down.position)
        consumeUntilUp()
        pressScope.release()
      }

      if (upOrCancel == null) {
        onUpOrCancel?.invoke()
        return@awaitPointerEventScope
      }

      // tap was successful.
      if (onDoubleTap == null) {
        onTap?.invoke(upOrCancel.position) // no need to check for double-tap.
        return@awaitPointerEventScope
      }

      // check for second tap
      val secondDown = awaitSecondDown(upOrCancel)

      if (secondDown == null) {
        onTap?.invoke(upOrCancel.position) // no valid second tap started
      } else {
        // Second tap down detected
        pressScope.reset()
        if (onPress !== NoPressGesture) {
          launch { pressScope.onPress(secondDown.position) }
        }

        try {
          // Might have a long second press as the second tap
          withTimeout(longPressTimeout) {
            val secondUp = waitForUpOrCancellation()
            if (secondUp != null) {
              secondUp.consumeDownChange()
              pressScope.release()
              onDoubleTap(secondUp.position)
            } else {
              pressScope.cancel()
              onTap?.invoke(upOrCancel.position)
            }
          }
        } catch (e: PointerEventTimeoutCancellationException) {
          // The first tap was valid, but the second tap is a long press.
          // notify for the first tap
          onTap?.invoke(upOrCancel.position)

          // notify for the long press
          onLongPress?.invoke(secondDown.position)
          consumeUntilUp()
          pressScope.release()
        }
      }
    }
  }
}

private suspend fun AwaitPointerEventScope.awaitSecondDown(
  firstUp: PointerInputChange
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
  val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
  var change: PointerInputChange
  // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
  do {
    change = awaitFirstDown()
  } while (change.uptimeMillis < minUptime)
  change
}

private suspend fun AwaitPointerEventScope.consumeUntilUp() {
  do {
    val event = awaitPointerEvent()
    event.changes.forEach { it.consumeAllChanges() }
  } while (event.changes.any { it.pressed })
}

private class PressGestureScopeImpl(
  density: Density
) : PressGestureScope, Density by density {
  private var isReleased = false
  private var isCanceled = false
  private val mutex = Mutex(locked = false)

  /**
   * Called when a gesture has been canceled.
   */
  fun cancel() {
    isCanceled = true
    mutex.unlock()
  }

  /**
   * Called when all pointers are up.
   */
  fun release() {
    isReleased = true
    mutex.unlock()
  }

  /**
   * Called when a new gesture has started.
   */
  fun reset() {
    mutex.tryLock() // If tryAwaitRelease wasn't called, this will be unlocked.
    isReleased = false
    isCanceled = false
  }

  override suspend fun awaitRelease() {
    if (!tryAwaitRelease()) {
      throw GestureCancellationException("The press gesture was canceled.")
    }
  }

  override suspend fun tryAwaitRelease(): Boolean {
    if (!isReleased && !isCanceled) {
      mutex.lock()
    }
    return isReleased
  }
}

suspend fun PointerInputScope.kurobaAwaitLongPressOrCancellation(
  initialDown: PointerInputChange
): PointerInputChange? {
  var longPress: PointerInputChange? = null
  var currentDown = initialDown
  val longPressTimeout = viewConfiguration.longPressTimeoutMillis
  return try {
    // wait for first tap up or long press
    withTimeout(longPressTimeout) {
      awaitPointerEventScope {
        var finished = false
        while (!finished && isActive) {
          val event = awaitPointerEvent(PointerEventPass.Main)
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
    }
    null
  } catch (_: TimeoutCancellationException) {
    longPress ?: initialDown
  }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
  changes.fastFirstOrNull { it.id == pointerId }?.pressed != true