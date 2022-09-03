package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat

class DeleteBookmarks(
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val actualDeleteJobs = mutableMapOf<ThreadDescriptor, DeleteJob>()

  suspend fun deleteFromMemoryCache(threadDescriptor: ThreadDescriptor, index: Int): ThreadBookmark? {
    // When new delete bookmark event is queued we need to force delete from the database and memory
    // all previously queued bookmarks
    mutex.withLock {
      actualDeleteJobs.remove(threadDescriptor)?.cancelDelete()
      actualDeleteJobs.values.forEach { deleteJob -> deleteJob.forceDelete() }
    }

    val deletedBookmark = bookmarksManager.removeBookmark(threadDescriptor)
    if (deletedBookmark != null) {
      mutex.withLock {
        val job = appScope.launch {
          try {
            val success = deleteFromDatabase(threadDescriptor)
            if (!success) {
              bookmarksManager.putBookmark(deletedBookmark, index)
            }
          } finally {
            mutex.withLock { actualDeleteJobs.remove(threadDescriptor) }
          }
        }

        actualDeleteJobs[threadDescriptor] = DeleteJob(job, threadDescriptor)
      }
    }

    return deletedBookmark
  }

  suspend fun undoDeletion(threadBookmark: ThreadBookmark, index: Int) {
    mutex.withLock {
      actualDeleteJobs[threadBookmark.threadDescriptor]?.cancelDelete()
    }

    bookmarksManager.putBookmark(threadBookmark, index)
  }

  private suspend fun deleteFromDatabase(threadDescriptor: ThreadDescriptor): Boolean {
    try {
      delay(AppConstants.deleteBookmarkTimeoutMs)
    } catch (error: Throwable) {
      val deleteJob = mutex.withLock { actualDeleteJobs[threadDescriptor] }

      if (deleteJob == null) {
        throw error
      }

      if (!deleteJob.continueDeletion) {
        logcat(TAG) { "Thread bookmark deletion canceled, bookmark: ${threadDescriptor}" }
        return false
      }

      logcat(TAG) { "Thread bookmark deletion forced, bookmark: ${threadDescriptor}" }
    }

    return withContext(NonCancellable) {
      bookmarksManager.removeBookmark(threadDescriptor)

      return@withContext kurobaExLiteDatabase.transaction {
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
      }
        .onSuccess {
          logcat(TAG) { "Deleted thread bookmark ${threadDescriptor} from the DB" }
        }
        .onFailure { error ->
          logcatError(TAG) {
            "Failed to delete bookmark ${threadDescriptor} from the DB, " +
              "error: ${error.asLogIfImportantOrErrorMessage()}"
          }
        }.isSuccess
    }
  }

  suspend fun deleteManyBookmarks(threadDescriptors: List<ThreadDescriptor>): Result<Int> {
    val deletedBookmarks = bookmarksManager.removeBookmarks(threadDescriptors)
    if (deletedBookmarks.isEmpty()) {
      return Result.success(0)
    }

    return kurobaExLiteDatabase.transaction {
      deletedBookmarks.forEach { threadBookmark ->
        val threadDescriptor = threadBookmark.threadDescriptor

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
      }

      return@transaction deletedBookmarks.size
    }
      .onSuccess { logcat(TAG) { "Deleted ${deletedBookmarks.size} bookmarks" } }
      .onFailure { error ->
        logcatError(TAG) {
          "Failed to delete ${deletedBookmarks.size} bookmarks from the DB, " +
            "error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
  }

  class DeleteJob(
    private val job: Job,
    val threadDescriptor: ThreadDescriptor
  ) {
    private val _continueDeletion: AtomicBoolean = AtomicBoolean(true)
    val continueDeletion: Boolean
      get() = _continueDeletion.get()

    @Synchronized
    fun forceDelete() {
      _continueDeletion.set(true)
      job.cancel()
    }

    @Synchronized
    fun cancelDelete() {
      _continueDeletion.set(false)
      job.cancel()
    }

  }

  companion object {
    private const val TAG = "DeleteBookmark"
  }

}