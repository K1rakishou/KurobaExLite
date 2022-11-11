package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

class ToggleBookmarkWatchState(
  private val kurobaExLiteDatabase: KurobaExLiteDatabase,
  private val bookmarksManager: BookmarksManager,
  private val restartBookmarkBackgroundWatcher: RestartBookmarkBackgroundWatcher,
) {

  suspend fun execute(threadDescriptor: ThreadDescriptor) {
    val updated = bookmarksManager.updateBookmark(
      threadDescriptor = threadDescriptor,
      updater = { threadBookmark ->
        threadBookmark.toggleWatching()
        return@updateBookmark true
      }
    )

    if (!updated) {
      return
    }

    val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
      ?: return

    kurobaExLiteDatabase
      .call { threadBookmarkDao.insertOrUpdateBookmark(threadBookmark) }
      .onSuccess { restartBookmarkBackgroundWatcher.restart(addInitialDelay = false) }
      .onFailure { error ->
        logcatError(TAG) {
          "Failed to update bookmark ${threadBookmark.threadDescriptor} in database, " +
            "error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
  }

  companion object {
    private const val TAG = "ToggleBookmarkWatchState"
  }

}