package com.github.k1rakishou.kurobaexlite.features.bookmarks

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.move
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.worker.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.DeleteBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.ReorderBookmarks
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.CatalogPagesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

class BookmarksScreenViewModel(
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers,
  private val bookmarksManager: BookmarksManager,
  private val catalogPagesRepository: CatalogPagesRepository,
  private val reorderBookmarks: ReorderBookmarks,
  private val deleteBookmarks: DeleteBookmarks,
) : BaseViewModel() {

  private val _bookmarksToMark = mutableStateMapOf<ThreadDescriptor, Unit>()
  val bookmarksToMark: Map<ThreadDescriptor, Unit>
    get() = _bookmarksToMark

  private val _bookmarksList = mutableStateListOf<ThreadBookmarkUi>()
  val bookmarksList: List<ThreadBookmarkUi>
    get() = _bookmarksList
  val bookmarksListFlow = snapshotFlow { _bookmarksList.toList() }

  private val _canUseFancyAnimations = mutableStateOf(false)
  val canUseFancyAnimations: State<Boolean>
    get() = _canUseFancyAnimations

  val backgroundWatcherEventsFlow = bookmarksManager.backgroundWatcherEventsFlow

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    _canUseFancyAnimations.value = !androidHelpers.isSlowDevice

    viewModelScope.launch {
      catalogPagesRepository.catalogPagesUpdatedFlow.collect { catalogDescriptor ->
        withContext(Dispatchers.Main) { processCatalogPageEvent(catalogDescriptor) }
      }
    }

    viewModelScope.launch {
      bookmarksManager.awaitUntilInitialized()

      val loadedBookmarks = reorderBookmarks.sort(bookmarksManager.getAllBookmarks())
        .map { threadBookmark ->
          return@map ThreadBookmarkUi.fromThreadBookmark(
            threadBookmark = threadBookmark,
            threadPage = null
          )
        }

      _bookmarksList.clear()
      _bookmarksList.addAll(loadedBookmarks)

      bookmarksManager.bookmarkEventsFlow.collect { event ->
        processBookmarkEvent(event)
      }
    }
  }

  suspend fun countDeadBookmarks(): Int {
    return bookmarksList.count { threadBookmarkUi -> threadBookmarkUi.threadBookmarkStatsUi.isDead() }
  }

  fun deleteBookmark(
    threadDescriptor: ThreadDescriptor,
    onBookmarkDeleted: (ThreadBookmark, Int) -> Unit
  ) {
    viewModelScope.launch {
      val oldPosition = _bookmarksList
        .indexOfFirst { threadBookmarkUi -> threadBookmarkUi.threadDescriptor == threadDescriptor }
        .takeIf { position -> position >= 0 }
        ?: 0

      val deletedBookmark = deleteBookmarks.deleteFromMemoryCache(threadDescriptor, oldPosition)
      if (deletedBookmark != null) {
        onBookmarkDeleted(deletedBookmark, oldPosition)
      }
    }
  }

  fun undoBookmarkDeletion(threadBookmark: ThreadBookmark, index: Int) {
    viewModelScope.launch {
      deleteBookmarks.undoDeletion(threadBookmark, index)
    }
  }

  fun onMove(from: Int, to: Int) {
    _bookmarksList.move(from, to)
  }

  suspend fun onMoveConfirmed(from: Int, to: Int) {
    val bookmarkDescriptorsOrdered = _bookmarksList
      .map { threadBookmarkUi -> threadBookmarkUi.threadDescriptor }

    if (reorderBookmarks.reorder(bookmarkDescriptorsOrdered)) {
      logcat(TAG, LogPriority.VERBOSE) {
        "Moved bookmark from ($from) to (${to}). " +
          "Bookmarks count: ${bookmarksList.size}"
      }
    } else {
      logcatError(TAG) {
        "Failed to move bookmark from ($from) to (${to}), moving it back. " +
          "Bookmarks count: ${bookmarksList.size}"
      }

      onMove(to, from)
    }
  }

  fun forceRefreshBookmarks(context: Context) {
    val appContext = context.applicationContext

    viewModelScope.launch {
      BookmarkBackgroundWatcherWorker.restartBackgroundWork(
        appContext = appContext,
        flavorType = androidHelpers.getFlavorType(),
        appSettings = appSettings,
        isInForeground = true,
        addInitialDelay = false
      )
    }
  }

  fun markBookmarks(threadDescriptors: List<ThreadDescriptor>) {
    val resultMap = mutableMapWithCap<ThreadDescriptor, Unit>(threadDescriptors.size)
    threadDescriptors.fastForEach { resultMap.put(it, Unit) }

    _bookmarksToMark.clear()
    _bookmarksToMark.putAll(resultMap)
  }

  fun clearMarkedBookmarks() {
    _bookmarksToMark.clear()
  }

  fun pruneDeadBookmarks(onFinished: (Result<Int>) -> Unit) {
    viewModelScope.launch {
      val deadBookmarks = bookmarksManager.getDeadBookmarkDescriptors()
      val deleteResult = deleteBookmarks.deleteManyBookmarks(deadBookmarks)
      onFinished(deleteResult)
    }
  }

  private suspend fun processCatalogPageEvent(catalogDescriptor: CatalogDescriptor) {
    bookmarksList.forEach { threadBookmarkUi ->
      if (threadBookmarkUi.threadBookmarkStatsUi.isDeadOrNotWatching()) {
        return@forEach
      }

      val threadDescriptor = threadBookmarkUi.threadDescriptor
      if (threadDescriptor.catalogDescriptor != catalogDescriptor) {
        return@forEach
      }

      val threadPage = catalogPagesRepository.getThreadPage(threadDescriptor)
        ?: return@forEach

      threadBookmarkUi.updatePagesFrom(threadPage)
    }
  }

  private suspend fun processBookmarkEvent(event: BookmarksManager.Event) {
    when (event) {
      is BookmarksManager.Event.Loaded -> {
        // no-op
      }
      is BookmarksManager.Event.Created -> {
        val newBookmarks = event.threadDescriptors.mapNotNull { threadDescriptor ->
          val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
            ?: return@mapNotNull null

          val threadPage = catalogPagesRepository.getThreadPage(threadDescriptor)

          return@mapNotNull ThreadBookmarkUi.fromThreadBookmark(
            threadBookmark = threadBookmark,
            threadPage = threadPage
          )
        }

        val index = event.index ?: 0
        _bookmarksList.addAll(index, newBookmarks)
      }
      is BookmarksManager.Event.Deleted -> {
        val bookmarksToDelete = event.threadDescriptors.toSet()
        val toDelete = mutableSetOf<ThreadBookmarkUi>()

        _bookmarksList.forEach { threadBookmarkUi ->
          if (threadBookmarkUi.threadDescriptor in bookmarksToDelete) {
            toDelete += threadBookmarkUi
          }
        }

        _bookmarksList.removeAll(toDelete)
      }
      is BookmarksManager.Event.Updated -> {
        event.threadDescriptors.forEach { threadDescriptor ->
          val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
            ?: return@forEach

          val index = _bookmarksList.indexOfFirst { threadBookmarkUi ->
            threadBookmarkUi.threadDescriptor == threadDescriptor
          }

          if (index >= 0) {
            // Bookmark exists, update it
            val threadPage = catalogPagesRepository.getThreadPage(threadDescriptor)

            _bookmarksList[index].update(
              threadBookmark = threadBookmark,
              threadPage = threadPage
            )
          } else {
            // Bookmark does not exist, create it
            val threadPage = catalogPagesRepository.getThreadPage(threadDescriptor)
            val threadBookmarkUi = ThreadBookmarkUi.fromThreadBookmark(
              threadBookmark = threadBookmark,
              threadPage = threadPage
            )

            _bookmarksList.add(0, threadBookmarkUi)
          }
        }
      }
    }
  }

  companion object {
    private const val TAG = "BookmarksScreenViewModel"
  }

}