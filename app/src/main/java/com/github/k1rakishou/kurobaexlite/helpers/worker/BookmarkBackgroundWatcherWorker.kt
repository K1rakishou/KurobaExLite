package com.github.k1rakishou.kurobaexlite.helpers.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.kpnc.KPNCAppInfo
import com.github.k1rakishou.kurobaexlite.helpers.kpnc.KPNCHelper
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.FetchThreadBookmarkInfo
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class BookmarkBackgroundWatcherWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  private val kpncHelper: KPNCHelper by inject(KPNCHelper::class.java)
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)
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

    val postUrlsToCheck = inputData.getStringArray(POST_URLS_TO_CHECK)?.toList() ?: emptyList()
    logcat(TAG) { "doWork() postUrlsToCheck: ${postUrlsToCheck.size}" }
    postUrlsToCheck.forEach { postUrlToCheck -> logcat(TAG) { "postUrlToCheck: ${postUrlToCheck}" } }

    val isInForeground = applicationVisibilityManager.isAppInForeground()
    if (!isInForeground && kpncHelper.isKpncEnabledAndAccountIsValid() && postUrlsToCheck.isEmpty()) {
      logcat(TAG) { "doWork() disabling WorkManager because KPNC is installed and application is in background" }
      // TODO: cancel the work here?
      return Result.success()
    }

    if (!isInForeground) {
      val kpncAppInfo = kpncHelper.kpncAppInfo()
      if (kpncAppInfo is KPNCAppInfo.Installed && !kpncAppInfo.isAccountValid) {
        logcat(TAG) { "doWork() kpncInfo is installed but account is not valid, resuming WorkManager" }
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

    val activeBookmarkDescriptors = bookmarksManager.getActiveBookmarkDescriptors()
    if (activeBookmarkDescriptors.isEmpty()) {
      logcat(TAG) { "activeBookmarkDescriptors are empty, doing nothing" }
      return Result.success()
    }

    fetchThreadBookmarkInfo.await(
      bookmarkDescriptorsToCheck = activeBookmarkDescriptors,
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
          addInitialDelay = true,
          postUrlsToCheck = emptyList()
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

    const val POST_URLS_TO_CHECK = "post_urls_to_check"

    suspend fun restartBackgroundWork(
      appContext: Context,
      flavorType: AndroidHelpers.FlavorType,
      appSettings: AppSettings,
      isInForeground: Boolean,
      addInitialDelay: Boolean,
      postUrlsToCheck: List<String>
    ) {
      val tag = AppConstants.WorkerTags.getUniqueTag(flavorType)
      logcat(TAG) { "restartBackgroundWork() called tag=${tag}, postUrlsToCheck=${postUrlsToCheck.size}" }

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val backgroundIntervalMillis = if (addInitialDelay && postUrlsToCheck.isEmpty()) {
        if (isInForeground) {
          appSettings.watcherIntervalForegroundSeconds.read().seconds * 1000L
        } else {
          appSettings.watcherIntervalBackgroundSeconds.read().seconds * 1000L
        }
      } else {
        1000L
      }

      val data = Data.Builder()
        .putStringArray(POST_URLS_TO_CHECK, postUrlsToCheck.toTypedArray())
        .build()

      val workRequest = OneTimeWorkRequestBuilder<BookmarkBackgroundWatcherWorker>()
        .addTag(tag)
        .setInitialDelay(backgroundIntervalMillis, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .setInputData(data)
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