package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.worker.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.logcat
import okhttp3.HttpUrl
import org.joda.time.DateTime
import java.util.concurrent.atomic.AtomicBoolean

class AddOrRemoveBookmark(
  private val appContext: Context,
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers,
  private val bookmarksManager: BookmarksManager,
  private val restartBookmarkBackgroundWatcher: RestartBookmarkBackgroundWatcher,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun addOrRemoveBookmark(
    threadDescriptor: ThreadDescriptor,
    bookmarkTitle: String,
    bookmarkThumbnail: HttpUrl
  ): Boolean {
    val didCreateBookmark = AtomicBoolean(false)

    val success = kurobaExLiteDatabase.call {
      if (bookmarksManager.contains(threadDescriptor)) {
        threadBookmarkDao.deleteBookmark(
          siteKey = threadDescriptor.siteKeyActual,
          boardCode = threadDescriptor.boardCode,
          threadNo = threadDescriptor.threadNo
        )

        bookmarksManager.removeBookmark(threadDescriptor)
        didCreateBookmark.set(false)

        logcat(TAG) { "Bookmark \'${threadDescriptor}\' removed" }
      } else {
        val threadBookmark = ThreadBookmark.create(
          threadDescriptor = threadDescriptor,
          createdOn = DateTime.now(),
          startWatching = appSettings.automaticallyStartWatchingBookmarks.read(),
          title = bookmarkTitle,
          thumbnailUrl = bookmarkThumbnail
        )

        threadBookmarkDao.insertOrUpdateBookmark(threadBookmark)
        bookmarksManager.putBookmark(threadBookmark)
        didCreateBookmark.set(true)

        logcat(TAG) { "Bookmark \'${threadDescriptor}\' created" }
      }
    }
      .onFailure { error ->
        logcatError(TAG) {
          "Failed to add or remove bookmark error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .isSuccess

    if (!success) {
      return false
    }

    if (didCreateBookmark.get()) {
      logcat(TAG) { "Bookmark created. Restarting the work" }
      restartBookmarkBackgroundWatcher.restart(addInitialDelay = false)
    } else {
      if (bookmarksManager.hasActiveBookmarks()) {
        logcat(TAG) { "Bookmark deleted. There are active bookmarks left, doing nothing" }
      } else {
        logcat(TAG) { "Bookmark deleted. No more active bookmarks, cancelling the work" }

        BookmarkBackgroundWatcherWorker.cancelBackgroundBookmarkWatching(
          appContext = appContext,
          flavorType = androidHelpers.getFlavorType()
        )
      }
    }

    return true
  }

  suspend fun addBookmarkIfNotExists(
    threadDescriptor: ThreadDescriptor,
    bookmarkTitle: String?,
    bookmarkThumbnail: HttpUrl?
  ): Boolean {
    val created = kurobaExLiteDatabase.call {
      if (bookmarksManager.contains(threadDescriptor)) {
        logcat(TAG) { "Bookmark \'${threadDescriptor}\' already exists" }
        return@call false
      }

      val threadBookmark = ThreadBookmark.create(
        threadDescriptor = threadDescriptor,
        createdOn = DateTime.now(),
        startWatching = appSettings.automaticallyStartWatchingBookmarks.read(),
        title = bookmarkTitle,
        thumbnailUrl = bookmarkThumbnail
      )

      threadBookmarkDao.insertOrUpdateBookmark(threadBookmark)
      bookmarksManager.putBookmark(threadBookmark)

      logcat(TAG) { "Bookmark \'${threadDescriptor}\' created" }
      return@call true
    }
      .onFailure { error ->
        logcatError(TAG) {
          "Failed to add or remove bookmark error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .getOrNull() ?: false

    if (!created) {
      return false
    }

    logcat(TAG) { "Bookmark created. Restarting the work" }
    restartBookmarkBackgroundWatcher.restart(addInitialDelay = false)

    return true
  }

  companion object {
    private const val TAG = "AddOrRemoveBookmark"
  }

}