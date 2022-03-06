package com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread

import android.os.SystemClock
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.logcat

class ThreadAutoUpdater(
  private val executeUpdate: suspend () -> Unit,
  private val canUpdate: suspend () -> Boolean
) {
  private val coroutineScope = KurobaCoroutineScope()
  private var autoUpdaterJob: Job? = null
  private var currentThreadDescriptor: ThreadDescriptor? = null
  private var updateIndex = 0
  private var nextRunTime: Long? = null

  val timeUntilNextUpdateMs: Long
    get() {
      val nextTime = nextRunTime ?: return 0L

      val delta = nextTime - SystemClock.elapsedRealtime()
      if (delta < 0L) {
        return 0L
      }

      return delta
    }

  fun resetTimer() {
    updateIndex = 0
    nextRunTime = SystemClock.elapsedRealtime() + (DELAYS.first() * 1000L)
  }

  fun runAutoUpdaterLoop(threadDescriptor: ThreadDescriptor) {
    if (currentThreadDescriptor == threadDescriptor) {
      return
    }

    stopAutoUpdaterLoop()

    nextRunTime = SystemClock.elapsedRealtime() + (DELAYS.first() * 1000L)
    currentThreadDescriptor = threadDescriptor

    autoUpdaterJob = coroutineScope.launch {
      logcat(tag = TAG) { "runAutoUpdaterLoop($threadDescriptor) start" }

      while (isActive) {
        delay(1000L)

        val now = SystemClock.elapsedRealtime()
        val nextRun = (nextRunTime ?: 0L)

        if (now >= nextRun && canUpdate()) {
          try {
            logcat(tag = TAG) { "executeUpdate($threadDescriptor)" }
            executeUpdate()
          } catch (error: Throwable) {
            logcatError(tag = TAG) { "executeUpdate() error: ${error.errorMessageOrClassName()}" }

            if (error is CancellationException) {
              throw error
            }
          }

          ++updateIndex
          nextRunTime = now + ((DELAYS.getOrNull(updateIndex) ?: DELAYS.last()) * 1000L)
        }
      }

      logcat(tag = TAG) { "runAutoUpdaterLoop($threadDescriptor) end" }
    }
  }

  fun stopAutoUpdaterLoop() {
    logcat(tag = TAG) { "stopAutoUpdaterLoop()" }

    autoUpdaterJob?.cancel()
    autoUpdaterJob = null

    currentThreadDescriptor = null
    updateIndex = 0
    nextRunTime = null
  }

  companion object {
    private const val TAG = "ThreadAutoUpdater"
    private val DELAYS = arrayOf<Long>(10, 15, 20, 25, 30, 40, 60, 90, 120, 180, 240, 300)
  }

}