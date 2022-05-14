package com.github.k1rakishou.kurobaexlite.ui.helpers.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull

suspend fun AwaitPointerEventScope.awaitPointerSlopOrCancellationWithPass(
  pointerId: PointerId,
  pointerType: PointerType = PointerType.Touch,
  pointerEventPass: PointerEventPass = PointerEventPass.Main,
  onPointerSlopReached: (change: PointerInputChange, overSlop: Offset) -> Unit
): PointerInputChange? {
  if (currentEvent.isPointerUp(pointerId)) {
    return null // The pointer has already been lifted, so the gesture is canceled
  }
  var offset = Offset.Zero
  val touchSlop = viewConfiguration.pointerSlop(pointerType)

  var pointer = pointerId

  while (true) {
    val event = awaitPointerEvent(pass = pointerEventPass)
    val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
    if (dragEvent.isConsumed) {
      return null
    } else if (dragEvent.changedToUpIgnoreConsumed()) {
      val otherDown = event.changes.fastFirstOrNull { it.pressed }
      if (otherDown == null) {
        // This is the last "up"
        return null
      } else {
        pointer = otherDown.id
      }
    } else {
      offset += dragEvent.positionChange()
      val distance = offset.getDistance()
      var acceptedDrag = false
      if (distance >= touchSlop) {
        val touchSlopOffset = offset / distance * touchSlop
        onPointerSlopReached(dragEvent, offset - touchSlopOffset)
        if (dragEvent.isConsumed) {
          acceptedDrag = true
        } else {
          offset = Offset.Zero
        }
      }

      if (acceptedDrag) {
        return dragEvent
      } else {
        awaitPointerEvent(PointerEventPass.Final)
        if (dragEvent.isConsumed) {
          return null
        }
      }
    }
  }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
  changes.fastFirstOrNull { it.id == pointerId }?.pressed != true

// This value was determined using experiments and common sense.
// We can't use zero slop, because some hypothetical desktop/mobile devices can send
// pointer events with a very high precision (but I haven't encountered any that send
// events with less than 1px precision)
private val mouseSlop = 0.125.dp
private val defaultTouchSlop = 18.dp // The default touch slop on Android devices
private val mouseToTouchSlopRatio = mouseSlop / defaultTouchSlop

// TODO(demin): consider this as part of ViewConfiguration class after we make *PointerSlop*
//  functions public (see the comment at the top of the file).
//  After it will be a public API, we should get rid of `touchSlop / 144` and return absolute
//  value 0.125.dp.toPx(). It is not possible right now, because we can't access density.
internal fun ViewConfiguration.pointerSlop(pointerType: PointerType): Float {
  return when (pointerType) {
    PointerType.Mouse -> touchSlop * mouseToTouchSlopRatio
    else -> touchSlop
  }
}