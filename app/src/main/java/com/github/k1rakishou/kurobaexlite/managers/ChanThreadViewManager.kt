package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.withLockNonCancellable
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanThreadView
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.sync.Mutex

class ChanThreadViewManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val chanThreadViews = mutableMapOf<ThreadDescriptor, ChanThreadView>()

  suspend fun insertOrUpdate(
    threadDescriptor: ThreadDescriptor,
    updater: ChanThreadView.() -> Unit
  ): ChanThreadView {
    return mutex.withLockNonCancellable {
      val chanThreadView = chanThreadViews.getOrPut(
        key = threadDescriptor,
        defaultValue = { ChanThreadView(threadDescriptor = threadDescriptor) }
      )

      updater(chanThreadView)
      chanThreadViews[threadDescriptor] = chanThreadView

      return@withLockNonCancellable chanThreadView
    }
  }

  suspend fun read(threadDescriptor: ThreadDescriptor): ChanThreadView? {
    return mutex.withLockNonCancellable { chanThreadViews[threadDescriptor] }
  }

}