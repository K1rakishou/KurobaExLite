package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.helpers.settings.LastVisitedCatalog
import com.github.k1rakishou.kurobaexlite.helpers.settings.LastVisitedThread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class LastVisitedEndpointManager {
  private val _lastVisitedCatalogFlow = MutableSharedFlow<LastVisitedCatalog>(extraBufferCapacity = 1)
  val lastVisitedCatalogFlow: SharedFlow<LastVisitedCatalog>
    get() = _lastVisitedCatalogFlow.asSharedFlow()

  private val _lastVisitedThreadFlow = MutableSharedFlow<LastVisitedThread>(extraBufferCapacity = 1)
  val lastVisitedThreadFlow: SharedFlow<LastVisitedThread>
    get() = _lastVisitedThreadFlow.asSharedFlow()

  fun notifyRestoreLastVisitedCatalog(lastVisitedCatalog: LastVisitedCatalog) {
    _lastVisitedCatalogFlow.tryEmit(lastVisitedCatalog)
  }

  fun notifyRestoreLastVisitedThread(lastVisitedThread: LastVisitedThread) {
    _lastVisitedThreadFlow.tryEmit(lastVisitedThread)
  }
}