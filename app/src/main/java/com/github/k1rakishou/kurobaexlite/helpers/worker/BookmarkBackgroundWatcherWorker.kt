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
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.FetchThreadBookmarkInfo
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class BookmarkBackgroundWatcherWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)
  private val applicationVisibilityManager: ApplicationVisibilityManager by inject(ApplicationVisibilityManager::class.java)
  private val fetchThreadBookmarkInfo: FetchThreadBookmarkInfo by inject(FetchThreadBookmarkInfo::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)

  override suspend fun doWork(): Result {
    val activeBookmarkDescriptors = bookmarksManager.getActiveBookmarkDescriptors()
    if (activeBookmarkDescriptors.isEmpty()) {
      return Result.success()
    }

    fetchThreadBookmarkInfo.await(
      bookmarkDescriptors = activeBookmarkDescriptors,
      updateCurrentlyOpenedThread = false
    )
      .onFailure { error ->
        logcatError(TAG) {
          "fetchThreadBookmarkInfo() error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .onSuccess { logcat(TAG) { "fetchThreadBookmarkInfo() success" } }

    val activeBookmarksCount = bookmarksManager.activeBookmarksCount()
    if (activeBookmarksCount > 0) {
      logcat(TAG) { "activeBookmarksCount: ${activeBookmarksCount} restarting the work" }

      withContext(NonCancellable) {
        restartBackgroundWork(
          appContext = applicationContext,
          flavorType = androidHelpers.getFlavorType(),
          isInForeground = applicationVisibilityManager.isAppInForeground()
        )
      }
    } else {
      logcat(TAG) { "activeBookmarksCount: ${activeBookmarksCount} the work loop is finished" }
    }

    return Result.success()
  }

  companion object {
    private const val TAG = "BookmarkBackgroundWatcherWorker"

    suspend fun restartBackgroundWork(
      appContext: Context,
      flavorType: AndroidHelpers.FlavorType,
      isInForeground: Boolean,
      addInitialDelay: Boolean = true,
    ) {
      val tag = AppConstants.WorkerTags.getUniqueTag(flavorType)
      logcat(TAG) { "restartBackgroundWork() called tag=$tag" }

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val backgroundIntervalMillis = if (addInitialDelay) {
        if (isInForeground) {
          30L * 1000L // 30 seconds
        } else {
          15 * 60L * 1000L // 15 minutes
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
        .enqueueUniqueWork(tag, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
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