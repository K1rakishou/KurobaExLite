package com.github.k1rakishou.kurobaexlite.helpers.util

import android.os.Looper

object BackgroundUtils {

  val isMainThread: Boolean
    get() = Thread.currentThread() === Looper.getMainLooper().thread

  fun ensureMainThread() {
    if (isMainThread) {
      return
    }

    val currentThread = Thread.currentThread()

    error("Cannot be executed on a background thread! Current thread is: ${currentThread.name} with id ${currentThread.id}")
  }

  fun ensureBackgroundThread() {
    if (!isMainThread) {
      return
    }

    error("Cannot be executed on the main thread!")
  }
}
