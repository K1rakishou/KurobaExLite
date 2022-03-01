package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanThreadViewManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val chanThreadViews = mutableMapOf<ThreadDescriptor, ChanThreadView>()

  suspend fun insertOrUpdate(
    threadDescriptor: ThreadDescriptor,
    updater: ChanThreadView.() -> Unit
  ) {
    mutex.withLock {
      val chanThreadView = chanThreadViews.getOrPut(
        key = threadDescriptor,
        defaultValue = { ChanThreadView() }
      )

      updater(chanThreadView)
      chanThreadViews[threadDescriptor] = chanThreadView
    }
  }

  suspend fun read(threadDescriptor: ThreadDescriptor): ChanThreadView? {
    return mutex.withLock { chanThreadViews[threadDescriptor] }
  }

}