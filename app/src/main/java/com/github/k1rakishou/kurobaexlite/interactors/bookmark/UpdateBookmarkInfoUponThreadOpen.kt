package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.ignore
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UpdateBookmarkInfoUponThreadOpen(
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val chanPostCache: IChanPostCache,
  private val parsedPostDataRepository: ParsedPostDataRepository,
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

      val bookmarkTitle = parsedPostDataRepository.formatThreadToolbarTitle(
        postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
        maxLength = AppConstants.bookmarkMaxTitleLength
      ) ?: return@launch

      val bookmarkThumbnail = chanPostCache.getOriginalPost(threadDescriptor)
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