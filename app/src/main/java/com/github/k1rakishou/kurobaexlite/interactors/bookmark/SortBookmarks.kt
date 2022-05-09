package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmarkSortOrder
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase

class SortBookmarks(
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {
  private var cachedThreadBookmarkSortOrders: MutableList<ThreadBookmarkSortOrder>? = null

  suspend fun await(bookmarks: List<ThreadBookmark>): List<ThreadBookmark> {
    if (bookmarks.isEmpty()) {
      return bookmarks
    }

    var threadBookmarkSortOrders = synchronized(this) {
      cachedThreadBookmarkSortOrders?.toMutableList()
    }

    if (threadBookmarkSortOrders.isNullOrEmpty()) {
      threadBookmarkSortOrders = kurobaExLiteDatabase.call {
        threadBookmarkDao.selectAllThreadBookmarkSortOrders().map { threadBookmarkSortOrderEntity ->
          ThreadBookmarkSortOrder(
            threadDescriptor = threadBookmarkSortOrderEntity.bookmarkKey.threadDescriptor,
            sortOrder = threadBookmarkSortOrderEntity.sortOrder
          )
        }
      }.onFailure { error ->
        logcatError(TAG) { "selectAllThreadBookmarkSortOrders() error: ${error.asLogIfImportantOrErrorMessage()}" }
      }.getOrNull()
        ?.toMutableList()
    }

    if (threadBookmarkSortOrders == null || threadBookmarkSortOrders.isEmpty()) {
      threadBookmarkSortOrders = (bookmarks.indices).map { index ->
        ThreadBookmarkSortOrder(
          threadDescriptor = bookmarks[index].threadDescriptor,
          sortOrder = -index
        )
      }.toMutableList()
    } else if (threadBookmarkSortOrders.size < bookmarks.size) {
      var minSortOrder = threadBookmarkSortOrders
        .minByOrNull { threadBookmarkSortOrder -> threadBookmarkSortOrder.sortOrder }
        ?.sortOrder
        ?.minus(1)
        ?: 0

      for (index in threadBookmarkSortOrders.size until bookmarks.size) {
        threadBookmarkSortOrders += ThreadBookmarkSortOrder(
          threadDescriptor = bookmarks[index].threadDescriptor,
          sortOrder = minSortOrder--
        )
      }
    }

    synchronized(this) {
      cachedThreadBookmarkSortOrders = threadBookmarkSortOrders.toMutableList()
    }

    val threadBookmarkSortOrdersMap = threadBookmarkSortOrders
      .associateBy { threadBookmarkSortOrder -> threadBookmarkSortOrder.threadDescriptor }

    var minSortOrder = threadBookmarkSortOrders
      .minByOrNull { threadBookmarkSortOrder -> threadBookmarkSortOrder.sortOrder }
      ?.sortOrder
      ?.minus(1)
      ?: 0

    return bookmarks.sortedBy { threadBookmark ->
      threadBookmarkSortOrdersMap[threadBookmark.threadDescriptor]
        ?.sortOrder
        ?: minSortOrder--
    }
  }

  companion object {
    private const val TAG = "LoadBookmarkSortOrders"
  }

}