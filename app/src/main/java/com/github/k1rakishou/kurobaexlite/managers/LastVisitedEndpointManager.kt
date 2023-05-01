package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.interactors.navigation.LoadNavigationHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LastVisitedEndpointManager(
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings
) {
  private val _lastVisitedCatalogFlow = MutableSharedFlow<LoadNavigationHistory.LastVisitedCatalog>(extraBufferCapacity = 1)
  val lastVisitedCatalogFlow: SharedFlow<LoadNavigationHistory.LastVisitedCatalog>
    get() = _lastVisitedCatalogFlow.asSharedFlow()

  private val _lastVisitedThreadFlow = MutableSharedFlow<LoadNavigationHistory.LastVisitedThread>(extraBufferCapacity = 1)
  val lastVisitedThreadFlow: SharedFlow<LoadNavigationHistory.LastVisitedThread>
    get() = _lastVisitedThreadFlow.asSharedFlow()

  fun notifyRestoreLastVisitedCatalog(lastVisitedCatalog: LoadNavigationHistory.LastVisitedCatalog) {
    appScope.launch {
      if (!appSettings.historyEnabled.read()) {
        return@launch
      }

      // Wait a little bit for the UI to initialize
      delay(250)
      _lastVisitedCatalogFlow.emit(lastVisitedCatalog)
    }
  }

  fun notifyRestoreLastVisitedThread(lastVisitedThread: LoadNavigationHistory.LastVisitedThread) {
    appScope.launch {
      if (!appSettings.historyEnabled.read()) {
        return@launch
      }

      // Wait a little bit for the UI to initialize
      delay(250)
      _lastVisitedThreadFlow.emit(lastVisitedThread)
    }
  }
}