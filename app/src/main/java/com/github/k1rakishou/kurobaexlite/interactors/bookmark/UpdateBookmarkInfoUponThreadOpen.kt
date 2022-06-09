package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.ignore
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UpdateBookmarkInfoUponThreadOpen(
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val chanCache: ChanCache,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun await(threadDescriptor: ThreadDescriptor) {
    if (!bookmarksManager.contains(threadDescriptor)) {
      return
    }

    appScope.launch {
      val threadBookmarkToUpdate = bookmarksManager.getBookmark(threadDescriptor)
        ?: return@launch

      if (threadBookmarkToUpdate.title.isNotNullNorEmpty() && threadBookmarkToUpdate.thumbnailUrl != null) {
        return@launch
      }

      val bookmarkTitle = parsedPostDataCache.formatThreadToolbarTitle(
        postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
        maxLength = AppConstants.bookmarkMaxTitleLength
      ) ?: return@launch

      val bookmarkThumbnail = chanCache.getOriginalPost(threadDescriptor)
        ?.images
        ?.firstOrNull()
        ?.thumbnailUrl
        ?: return@launch

      kurobaExLiteDatabase.call {
        if (threadBookmarkToUpdate.title.isNullOrEmpty()) {
          threadBookmarkToUpdate.title = bookmarkTitle
        }

        if (threadBookmarkToUpdate.thumbnailUrl == null) {
          threadBookmarkToUpdate.thumbnailUrl = bookmarkThumbnail
        }

        threadBookmarkDao.updateMany(listOf(threadBookmarkToUpdate.toThreadBookmarkEntity()))
        bookmarksManager.putBookmark(threadBookmarkToUpdate)
      }
        .onFailure { error ->
          logcatError(TAG) {
            "Failed to update bookmark error: ${error.asLogIfImportantOrErrorMessage()}"
          }
        }
        .ignore()
    }
  }

  companion object {
    private const val TAG = "UpdateBookmarkInfoUponThreadOpen"
  }

}