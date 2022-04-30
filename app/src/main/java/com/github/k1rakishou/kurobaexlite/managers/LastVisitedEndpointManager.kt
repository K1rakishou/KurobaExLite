package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.helpers.settings.LastVisitedCatalog
import com.github.k1rakishou.kurobaexlite.helpers.settings.LastVisitedThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LastVisitedEndpointManager(
  private val appScope: CoroutineScope
) {
  private val _lastVisitedCatalogFlow = MutableSharedFlow<LastVisitedCatalog>(extraBufferCapacity = 1)
  val lastVisitedCatalogFlow: SharedFlow<LastVisitedCatalog>
    get() = _lastVisitedCatalogFlow.asSharedFlow()

  private val _lastVisitedThreadFlow = MutableSharedFlow<LastVisitedThread>(extraBufferCapacity = 1)
  val lastVisitedThreadFlow: SharedFlow<LastVisitedThread>
    get() = _lastVisitedThreadFlow.asSharedFlow()

  fun notifyRestoreLastVisitedCatalog(lastVisitedCatalog: LastVisitedCatalog) {
    appScope.launch {
      // Wait a little bit for the UI to initialize
      delay(500)
      _lastVisitedCatalogFlow.emit(lastVisitedCatalog)
    }
  }

  fun notifyRestoreLastVisitedThread(lastVisitedThread: LastVisitedThread) {
    appScope.launch {
      // Wait a little bit for the UI to initialize
      delay(500)
      _lastVisitedThreadFlow.emit(lastVisitedThread)
    }
  }
}