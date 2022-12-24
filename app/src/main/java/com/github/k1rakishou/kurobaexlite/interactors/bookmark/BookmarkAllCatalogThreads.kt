package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl
import org.joda.time.DateTime
import java.util.concurrent.atomic.AtomicBoolean

class BookmarkAllCatalogThreads(
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings,
  private val chanPostCache: IChanPostCache,
  private val bookmarksManager: BookmarksManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val restartBookmarkBackgroundWatcher: RestartBookmarkBackgroundWatcher,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val working = AtomicBoolean(false)

  fun await(catalogDescriptor: CatalogDescriptor) {
    appScope.launch(dispatcher) {
      if (!working.compareAndSet(false, true)) {
        return@launch
      }

      try {
        doTheWork(catalogDescriptor)
      } finally {
        working.set(false)
      }
    }
  }

  private suspend fun doTheWork(catalogDescriptor: CatalogDescriptor) {
    val catalogThreads = chanPostCache.getCatalogThreads(catalogDescriptor)
    if (catalogThreads.isEmpty()) {
      return
    }

    val bookmarksToCreate = catalogThreads.mapNotNull { originalPostData ->
      val bookmarkDescriptor = originalPostData.postDescriptor.threadDescriptor

      val bookmarkTitle = parsedPostDataCache.formatThreadToolbarTitle(
        postDescriptor = bookmarkDescriptor.toOriginalPostDescriptor(),
        maxLength = AppConstants.bookmarkMaxTitleLength
      ) ?: return@mapNotNull null

      val bookmarkThumbnail = originalPostData.images
        ?.firstOrNull()
        ?.thumbnailUrl
        ?: return@mapNotNull null

      return@mapNotNull BookmarkToCreate(
        threadDescriptor = originalPostData.postDescriptor.threadDescriptor,
        bookmarkTitle = bookmarkTitle,
        bookmarkThumbnail = bookmarkThumbnail
      )
    }

    logcat(TAG) { "catalogThreads=${catalogThreads.size}, bookmarksToCreate=${bookmarksToCreate.size}" }
    createBookmarks(bookmarksToCreate)
  }

  private suspend fun createBookmarks(bookmarks: List<BookmarkToCreate>): Boolean {
    if (bookmarks.isEmpty()) {
      logcat(TAG) { "bookmarks are empty" }
      return false
    }

    val didCreateBookmark = AtomicBoolean(false)

    val success = kurobaExLiteDatabase.transaction {
      bookmarks.forEachIndexed { index, bookmarkToCreate ->
        val threadDescriptor = bookmarkToCreate.threadDescriptor
        val bookmarkTitle = bookmarkToCreate.bookmarkTitle
        val bookmarkThumbnail = bookmarkToCreate.bookmarkThumbnail

        if (bookmarksManager.contains(threadDescriptor)) {
          logcat(TAG, LogPriority.VERBOSE) {
            "[${index + 1}/${bookmarks.size}] Skipping ${threadDescriptor} because it's already bookmarked"
          }

          return@forEachIndexed
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
        didCreateBookmark.set(true)

        logcat(TAG, LogPriority.VERBOSE) {
          "[${index + 1}/${bookmarks.size}] Bookmark ${threadDescriptor} created"
        }
      }
    }
      .onFailure { error ->
        logcatError(TAG) {
          "Failed to add or remove bookmarks error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .isSuccess

    if (!success) {
      return false
    }

    if (didCreateBookmark.get()) {
      logcat(TAG) { "Bookmark(s) created. Restarting the work" }
      restartBookmarkBackgroundWatcher.restart(addInitialDelay = false)
    } else {
      logcat(TAG) { "No bookmarks were created. Doing nothing." }
    }

    return true
  }

  data class BookmarkToCreate(
    val threadDescriptor: ThreadDescriptor,
    val bookmarkTitle: String,
    val bookmarkThumbnail: HttpUrl
  )

  companion object {
    private const val TAG = "BookmarkAllCatalogThreads"
  }

}