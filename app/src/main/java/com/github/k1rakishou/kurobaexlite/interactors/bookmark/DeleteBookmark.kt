package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeleteBookmark(
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val actualDeleteJobs = mutableMapOf<ThreadDescriptor, Job>()

  suspend fun deleteFrom(threadDescriptor: ThreadDescriptor, index: Int): ThreadBookmark? {
    val deletedBookmark = bookmarksManager.removeBookmark(threadDescriptor)
    if (deletedBookmark != null) {
      mutex.withLock {
        actualDeleteJobs.remove(threadDescriptor)?.cancel()
        actualDeleteJobs[threadDescriptor] = appScope.launch {
          try {
            val success = actualDeleteBookmark(threadDescriptor)
            if (!success) {
              bookmarksManager.putBookmark(deletedBookmark, index)
            }
          } finally {
            mutex.withLock { actualDeleteJobs.remove(threadDescriptor) }
          }
        }
      }
    }

    return deletedBookmark
  }

  suspend fun undoDeletion(threadBookmark: ThreadBookmark, index: Int) {
    mutex.withLock {
      actualDeleteJobs.remove(threadBookmark.threadDescriptor)?.cancel()
    }

    bookmarksManager.putBookmark(threadBookmark, index)
  }

  private suspend fun actualDeleteBookmark(threadDescriptor: ThreadDescriptor): Boolean {
    delay(AppConstants.deleteBookmarkTimeoutMs)

    if (!actualDeleteJobs.containsKey(threadDescriptor)) {
      return true
    }

    bookmarksManager.removeBookmark(threadDescriptor)

    return kurobaExLiteDatabase.transaction {
      threadBookmarkDao.deleteBookmark(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )

      threadBookmarkDao.deleteThreadBookmarkSortOrderEntity(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )
    }.onFailure { error ->
      logcatError(TAG) {
        "Failed to delete bookmark ${threadDescriptor} from the DB, " +
          "error: ${error.asLogIfImportantOrErrorMessage()}"
      }
    }.isSuccess
  }

  companion object {
    private const val TAG = "DeleteBookmark"
  }

}