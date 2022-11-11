package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.worker.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class RestartBookmarkBackgroundWatcher(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val androidHelpers: AndroidHelpers,
  private val appSettings: AppSettings,
  private val applicationVisibilityManager: ApplicationVisibilityManager,
) {
  private val jobRef = AtomicReference<Job?>(null)

  fun restart(addInitialDelay: Boolean) {
    jobRef.getAndSet(null)?.cancel()

    val job = appScope.launch {
      BookmarkBackgroundWatcherWorker.restartBackgroundWork(
        appContext = appContext,
        flavorType = androidHelpers.getFlavorType(),
        appSettings = appSettings,
        isInForeground = applicationVisibilityManager.isAppInForeground(),
        addInitialDelay = addInitialDelay
      )
    }

    jobRef.set(job)
  }

}