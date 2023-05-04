package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Taken from https://github.com/aclassen/ComposeReorderable
 * */

fun Modifier.detectReorder(state: ReorderableState) =
  this.then(
    Modifier.pointerInput(Unit) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var drag: PointerInputChange?
        var overSlop = Offset.Zero
        do {
          drag = awaitPointerSlopOrCancellation(down.id, down.type) { change, over ->
            change.consume()
            overSlop = over
          }
        } while (drag != null && !drag.isConsumed)
        if (drag != null) {
          state.ch.trySend(StartDrag(down.id, overSlop))
        }
      }
    }
  )