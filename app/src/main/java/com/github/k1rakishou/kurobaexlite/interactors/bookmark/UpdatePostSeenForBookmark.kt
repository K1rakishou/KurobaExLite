package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import logcat.LogPriority
import logcat.logcat

class UpdatePostSeenForBookmark(
  private val appScope: CoroutineScope,
  private val chanCache: ChanCache,
  private val bookmarksManager: BookmarksManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val executor = DebouncingCoroutineExecutor(appScope, dispatcher)

  fun onPostViewed(
    postDescriptor: PostDescriptor
  ) {
    executor.post(timeout = 350L) {
      if (!bookmarksManager.contains(postDescriptor.threadDescriptor)) {
        return@post
      }

      val newPostsCount = chanCache.getNewPostsCount(postDescriptor)
      val bookmarkUpdated = bookmarksManager.onPostViewed(postDescriptor, newPostsCount)

      if (!bookmarkUpdated) {
        return@post
      }

      val threadBookmark = bookmarksManager.getBookmark(postDescriptor.threadDescriptor)
        ?: return@post

      kurobaExLiteDatabase
        .call { threadBookmarkDao.update(threadBookmark.toThreadBookmarkEntity()) }
        .onFailure { error ->
          logcatError(TAG) {
            "Failed to update bookmark ${threadBookmark.threadDescriptor} in database, " +
              "error: ${error.asLogIfImportantOrErrorMessage()}"
          }
        }

      logcat(TAG, LogPriority.VERBOSE) {
        "bookmarkDescriptor=${threadBookmark.threadDescriptor}, " +
          "unseenPostsCount=${threadBookmark.unseenPostsCount()}, " +
          "hasUnreadReplies=${threadBookmark.hasUnreadReplies()}, " +
          "state=${threadBookmark.stateToString()}"
      }
    }
  }

  companion object {
    private const val TAG = "UpdatePostSeenForBookmark"
  }

}