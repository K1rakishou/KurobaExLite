package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.logcat

class PersistBookmarks(
  private val bookmarksManager: BookmarksManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun await(threadDescriptors: List<ThreadDescriptor>): Result<Unit> {
    if (threadDescriptors.isEmpty()) {
      logcat(TAG) { "threadDescriptors isEmpty" }
      return Result.success(Unit)
    }

    return Result.Try {
      val threadBookmarksToPersist = bookmarksManager.getBookmarks(threadDescriptors)
      if (threadBookmarksToPersist.isEmpty()) {
        logcat(TAG) { "threadBookmarksToPersist isEmpty" }
        return@Try
      }

      logcat(TAG) { "persisting ${threadBookmarksToPersist.size} bookmarks" }

      kurobaExLiteDatabase
        .transaction { threadBookmarkDao.insertOrUpdateManyBookmarks(threadBookmarksToPersist) }
        .onFailure { error ->
          logcatError(TAG) {
            "insertOrUpdateManyBookmarks(${threadBookmarksToPersist.size}) " +
              "error: ${error.asLogIfImportantOrErrorMessage()}"
          }
        }
        .unwrap()
    }
  }

  companion object {
    private const val TAG = "PersistBookmarks"
  }

}