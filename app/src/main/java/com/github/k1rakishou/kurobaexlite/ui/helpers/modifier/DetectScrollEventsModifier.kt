package com.github.k1rakishou.kurobaexlite.ui.helpers.modifier

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.os.HandlerCompat

fun Modifier.detectListScrollEvents(
  token: String,
  notifyIntervalMs: Long = 100L,
  onListScrolled: (Float) -> Unit
): Modifier {
  return composed {
    val nestedScrollConnection = remember {
      object : NestedScrollConnection {
        private var lastCallTime = 0L
        private var accumulatedScrollOffsetY: Float = 0f

        private val handler = Handler(Looper.getMainLooper())

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          val now = SystemClock.elapsedRealtime()
          accumulatedScrollOffsetY += available.y

          // Only notify about scroll events every [notifyIntervalMs] ms because otherwise stuff may
          // get laggy.
          if (now - lastCallTime > notifyIntervalMs) {
            lastCallTime = now
            onListScrolled(accumulatedScrollOffsetY)
            accumulatedScrollOffsetY = 0f
          }

          handler.removeCallbacksAndMessages(token)
          HandlerCompat.postDelayed(
            handler,
            {
              onListScrolled(accumulatedScrollOffsetY)
              accumulatedScrollOffsetY = 0f
            },
            token,
            notifyIntervalMs
          )

          return Offset.Zero
        }
      }
    }

    return@composed Modifier.nestedScroll(nestedScrollConnection)
  }
}