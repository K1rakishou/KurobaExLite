package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadBookmarkSortOrderEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadKey
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmarkSortOrder
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

class ReorderBookmarks(
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun reorder(bookmarkDescriptorsOrdered: List<ThreadDescriptor>): Boolean {
    return kurobaExLiteDatabase.transaction {
      val existingBookmarkKeyWithDatabaseId = threadBookmarkDao.selectExistingKeys()
      val threadBookmarkDatabaseIdMap = mutableMapWithCap<ThreadDescriptor, Long>(existingBookmarkKeyWithDatabaseId.size)

      existingBookmarkKeyWithDatabaseId.forEach { bookmarkKeyWithDatabaseId ->
        threadBookmarkDatabaseIdMap[bookmarkKeyWithDatabaseId.threadKey.threadDescriptor] =
          bookmarkKeyWithDatabaseId.databaseId
      }

      var startOrder = -bookmarkDescriptorsOrdered.size

      val threadBookmarkSortOrderEntities = bookmarkDescriptorsOrdered.mapNotNull { threadDescriptor ->
        val databaseId = threadBookmarkDatabaseIdMap[threadDescriptor]
          ?: return@mapNotNull null

        return@mapNotNull ThreadBookmarkSortOrderEntity(
          ownerDatabaseId = databaseId,
          bookmarkKey = ThreadKey.fromThreadDescriptor(threadDescriptor),
          sortOrder = startOrder++
        )
      }

      threadBookmarkDao.insertOrUpdateThreadBookmarkSortOrderEntities(threadBookmarkSortOrderEntities)
    }.onFailure { error ->
      logcatError(TAG) {
        "Thread bookmarks database reordering failed, " +
          "error: ${error.asLogIfImportantOrErrorMessage()}"
      }
    }.isSuccess
  }

  suspend fun sort(bookmarks: List<ThreadBookmark>): List<ThreadBookmark> {
    if (bookmarks.isEmpty()) {
      return bookmarks
    }

    var threadBookmarkSortOrders = kurobaExLiteDatabase.call {
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