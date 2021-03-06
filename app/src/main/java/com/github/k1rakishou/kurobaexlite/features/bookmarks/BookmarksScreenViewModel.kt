package com.github.k1rakishou.kurobaexlite.features.bookmarks

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.move
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.worker.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.DeleteBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.ReorderBookmarks
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repoository.CatalogPagesRepository
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class BookmarksScreenViewModel : BaseViewModel() {
  private val appSettings: AppSettings by inject(AppSettings::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)
  private val catalogPagesRepository: CatalogPagesRepository by inject(CatalogPagesRepository::class.java)
  private val reorderBookmarks: ReorderBookmarks by inject(ReorderBookmarks::class.java)
  private val deleteBookmarks: DeleteBookmarks by inject(DeleteBookmarks::class.java)

  private val _bookmarksToMark = mutableStateMapOf<ThreadDescriptor, Unit>()
  val bookmarksToMark: Map<ThreadDescriptor, Unit>
    get() = _bookmarksToMark

  private val _bookmarksList = mutableStateListOf<ThreadBookmarkUi>()
  val bookmarksList: List<ThreadBookmarkUi>
    get() = _bookmarksList

  private val _canUseFancyAnimations = mutableStateOf(false)
  val canUseFancyAnimations: State<Boolean>
    get() = _canUseFancyAnimations

  val backgroundWatcherEventsFlow = bookmarksManager.backgroundWatcherEventsFlow

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    _canUseFancyAnimations.value = !androidHelpers.isSlowDevice

    viewModelScope.launch {
      catalogPagesRepository.catalogPagesUpdatedFlow.collect { catalogDescriptor ->
        processCatalogPageEvent(catalogDescriptor)
      }
    }

    viewModelScope.launch {
      bookmarksManager.awaitUntilInitialized()

      val loadedBookmarks = reorderBookmarks.sort(bookmarksManager.getAllBookmarks())
        .mapNotNull { threadBookmark ->
          return@mapNotNull ThreadBookmarkUi.fromThreadBookmark(
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

  fun deleteBookmark(threadDescriptor: ThreadDescriptor, onBookmarkDeleted: (ThreadBookmark, Int) -> Unit) {
    viewModelScope.launch {
      val oldPosition = _bookmarksList
        .indexOfFirst { threadBookmarkUi -> threadBookmarkUi.threadDescriptor == threadDescriptor }
        .takeIf { position -> position >= 0 }
        ?: 0

      val deletedBookmark = deleteBookmarks.deleteFrom(threadDescriptor, oldPosition)
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

  fun pruneInactiveBookmarks(onFinished: (Result<Int>) -> Unit) {
    viewModelScope.launch {
      val inactiveBookmarks = bookmarksManager.getInactiveBookmarkDescriptors()
      val deleteResult = deleteBookmarks.deleteManyBookmarks(inactiveBookmarks)
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
      BookmarksManager.Event.Loaded -> {
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

            _bookmarksList[index].updateStatsFrom(
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

            if (threadBookmarkUi != null) {
              _bookmarksList.add(0, threadBookmarkUi)
            }
          }
        }
      }
    }
  }

  companion object {
    private const val TAG = "BookmarksScreenViewModel"
  }

}