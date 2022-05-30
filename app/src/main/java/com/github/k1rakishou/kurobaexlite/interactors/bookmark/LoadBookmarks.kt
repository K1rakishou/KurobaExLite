package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.resumeValueSafe
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.worker.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.logcat

class LoadBookmarks(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers,
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val bookmarksManager: BookmarksManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val bookmarksLoaded = AtomicBoolean(false)

  suspend fun executeSuspend(restartWork: Boolean): Result<Boolean> {
    return suspendCancellableCoroutine { cancellableContinuation ->
      execute(restartWork = restartWork) { result ->
        cancellableContinuation.resumeValueSafe(result)
      }
    }
  }

  fun execute(restartWork: Boolean, onFinished: (Result<Boolean>) -> Unit) {
    if (bookmarksLoaded.get()) {
      onFinished(Result.success(false))
      return
    }

    appScope.launch(dispatcher) {
      if (!bookmarksLoaded.compareAndSet(false, true)) {
        onFinished(Result.success(false))
        return@launch
      }

      try {
        val exception = kurobaExLiteDatabase.transaction {
          val threadBookmarks = threadBookmarkDao.selectAllBookmarksWithReplies()
            .map { threadBookmarkEntityWithReplies ->
              return@map ThreadBookmark.fromThreadBookmarkEntityWithReplies(
                threadBookmarkEntityWithReplies
              )
            }

          bookmarksManager.init(threadBookmarks)
          return@transaction threadBookmarks.size
        }
          .onFailure { error ->
            logcatError(TAG) { "Load bookmarks from database error: ${error.asLogIfImportantOrErrorMessage()}" }
          }
          .onSuccess { loadedBookmarksCount ->
            logcat(TAG) { "Loaded ${loadedBookmarksCount} bookmarks from database" }
          }
          .exceptionOrNull()

        if (exception != null) {
          onFinished(Result.failure(exception))
          return@launch
        }

        val activeBookmarksCount = bookmarksManager.activeBookmarksCount()
        if (activeBookmarksCount > 0) {
          if (restartWork) {
            logcat(TAG) { "activeBookmarksCount is greater than zero (${activeBookmarksCount}) restarting the work" }

            BookmarkBackgroundWatcherWorker.restartBackgroundWork(
              appContext = appContext,
              flavorType = androidHelpers.getFlavorType(),
              appSettings = appSettings,
              isInForeground = applicationVisibilityManager.isAppInForeground(),
              addInitialDelay = false
            )
          } else {
            logcat(TAG) { "activeBookmarksCount is greater than zero but restartWork flag is false" }
          }
        } else {
          logcat(TAG) { "No active bookmarks loaded, doing nothing" }
        }

        onFinished(Result.success(true))
      } catch (error: Throwable) {
        onFinished(Result.failure(error))
      }
    }
  }

  companion object {
    private const val TAG = "LoadBookmarks"
  }

}