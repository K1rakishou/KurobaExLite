package com.github.k1rakishou.kurobaexlite.helpers.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.kpnc.KPNSAccountInfo
import com.github.k1rakishou.kurobaexlite.helpers.kpnc.KPNSHelper
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.FetchThreadBookmarkInfo
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class BookmarkBackgroundWatcherWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  private val kpncHelper: KPNSHelper by inject(KPNSHelper::class.java)
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)
  private val siteManager: SiteManager by inject(SiteManager::class.java)
  private val applicationVisibilityManager: ApplicationVisibilityManager by inject(ApplicationVisibilityManager::class.java)
  private val fetchThreadBookmarkInfo: FetchThreadBookmarkInfo by inject(FetchThreadBookmarkInfo::class.java)
  private val appSettings: AppSettings by inject(AppSettings::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val loadBookmarks: LoadBookmarks by inject(LoadBookmarks::class.java)

  override suspend fun doWork(): Result {
    try {
      return doWorkInternal()
    } finally {
      bookmarksManager.onBackgroundWatcherWorkFinished()
    }
  }

  private suspend fun doWorkInternal(): Result {
    logcat(TAG) { "doWork() start" }

    val isInForeground = applicationVisibilityManager.isAppInForeground()
    if (!isInForeground && kpncHelper.isKpncEnabledAndAccountIsValid()) {
      logcat(TAG) { "doWork() disabling WorkManager because KPNC is enabled and application is in background" }
      return Result.success()
    }

    if (!isInForeground) {
      val kpncAppInfo = kpncHelper.kpnsAccountInfo()
      if (kpncAppInfo is KPNSAccountInfo.Success && !kpncAppInfo.isAccountValid) {
        logcat(TAG) { "doWork() kpncInfo is enabled but account is not valid, resuming WorkManager" }
      }
    } else {
      logcat(TAG) { "doWork() application is in foreground, resuming WorkManager" }
    }

    val loadBookmarksResult = loadBookmarks.executeSuspend()
    if (loadBookmarksResult.isFailure) {
      val error = loadBookmarksResult.exceptionOrThrow()
      logcat(TAG) { "doWork() loadBookmarks.executeSuspend() error: ${error.asLogIfImportantOrErrorMessage()}" }
      return Result.failure()
    }

    val bookmarkDescriptorsToCheck = bookmarksManager.getActiveBookmarkDescriptors()
    if (bookmarkDescriptorsToCheck.isEmpty()) {
      logcat(TAG) { "bookmarkDescriptorsToCheck are empty, doing nothing" }
      return Result.success()
    }

    fetchThreadBookmarkInfo.await(
      bookmarkDescriptorsToCheck = bookmarkDescriptorsToCheck,
      updateCurrentlyOpenedThread = false
    )
      .onFailure { error ->
        logcatError(TAG) {
          "doWork() fetchThreadBookmarkInfo() error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .onSuccess { logcat(TAG) { "doWork() fetchThreadBookmarkInfo() success" } }

    val activeBookmarksCount = bookmarksManager.watchingBookmarksCount()
    if (activeBookmarksCount > 0) {
      logcat(TAG) { "doWork() activeBookmarksCount: ${activeBookmarksCount} restarting the work" }

      withContext(NonCancellable) {
        restartBackgroundWork(
          appContext = applicationContext,
          flavorType = androidHelpers.getFlavorType(),
          appSettings = appSettings,
          isInForeground = isInForeground,
          addInitialDelay = true
        )
      }
    } else {
      logcat(TAG) { "doWork() activeBookmarksCount: ${activeBookmarksCount} the work loop is finished" }
    }

    logcat(TAG) { "doWork() end" }
    return Result.success()
  }

  companion object {
    private const val TAG = "BookmarkBackgroundWatcherWorker"

    suspend fun restartBackgroundWork(
      appContext: Context,
      flavorType: AndroidHelpers.FlavorType,
      appSettings: AppSettings,
      isInForeground: Boolean,
      addInitialDelay: Boolean
    ) {
      val tag = AppConstants.WorkerTags.getUniqueTag(flavorType)
      logcat(TAG) { "restartBackgroundWork() called tag=${tag}" }

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val backgroundIntervalMillis = if (addInitialDelay) {
        if (isInForeground) {
          appSettings.watcherIntervalForegroundSeconds.read().seconds * 1000L
        } else {
          appSettings.watcherIntervalBackgroundSeconds.read().seconds * 1000L
        }
      } else {
        1000L
      }

      val workRequest = OneTimeWorkRequestBuilder<BookmarkBackgroundWatcherWorker>()
        .addTag(tag)
        .setInitialDelay(backgroundIntervalMillis, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

      WorkManager
        .getInstance(appContext)
        .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, workRequest)
        .result
        .await()

      logcat(TAG) {
        "restartBackgroundWork() enqueued work with tag $tag, " +
          "backgroundIntervalMillis=$backgroundIntervalMillis"
      }
    }

    suspend fun cancelBackgroundBookmarkWatching(
      appContext: Context,
      flavorType: AndroidHelpers.FlavorType,
    ) {
      val tag = AppConstants.WorkerTags.getUniqueTag(flavorType)
      logcat(TAG) { "cancelBackgroundBookmarkWatching() called tag=$tag" }

      WorkManager
        .getInstance(appContext)
        .cancelUniqueWork(tag)
        .result
        .await()

      logcat(TAG) { "cancelBackgroundBookmarkWatching() work with tag $tag canceled" }
    }
  }

}