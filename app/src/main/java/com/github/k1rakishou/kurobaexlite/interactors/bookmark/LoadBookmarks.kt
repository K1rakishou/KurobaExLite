package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeValueSafe
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.logcat
import java.util.concurrent.atomic.AtomicBoolean

class LoadBookmarks(
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase,
  private val restartBookmarkBackgroundWatcher: RestartBookmarkBackgroundWatcher,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val bookmarksLoaded = AtomicBoolean(false)

  suspend fun executeSuspend(): Result<Boolean> {
    return suspendCancellableCoroutine { cancellableContinuation ->
      execute(shouldRestartWork = false) { result ->
        cancellableContinuation.resumeValueSafe(result)
      }
    }
  }

  fun execute(shouldRestartWork: Boolean, onFinished: (Result<Boolean>) -> Unit) {
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

        restartWorkIfNeeded(shouldRestartWork)
        onFinished(Result.success(true))
      } catch (error: Throwable) {
        onFinished(Result.failure(error))
      }
    }
  }

  private suspend fun restartWorkIfNeeded(shouldRestartWork: Boolean) {
    val activeBookmarksCount = bookmarksManager.watchingBookmarksCount()
    if (activeBookmarksCount <= 0) {
      logcat(TAG) { "No active bookmarks loaded, doing nothing" }
      return
    }

    if (!shouldRestartWork) {
      logcat(TAG) { "activeBookmarksCount is greater than zero but restartWork flag is false" }
      return
    }

    logcat(TAG) { "activeBookmarksCount is greater than zero (${activeBookmarksCount}) restarting the work" }
    restartBookmarkBackgroundWatcher.restart(addInitialDelay = false)
  }

  companion object {
    private const val TAG = "LoadBookmarks"
  }

}