package com.github.k1rakishou.kurobaexlite.helpers

import android.os.Looper

object BackgroundUtils {

  val isMainThread: Boolean
    get() = Thread.currentThread() === Looper.getMainLooper().thread

  fun ensureMainThread() {
    if (isMainThread) {
      return
    }

    error("Cannot be executed on a background thread!")
  }

  fun ensureBackgroundThread() {
    if (!isMainThread) {
      return
    }

    error("Cannot be executed on the main thread!")
  }
}
