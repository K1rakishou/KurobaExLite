package com.github.k1rakishou.kurobaexlite.features.bookmarks

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.mutableIteration
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.SortBookmarks
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.repoository.CatalogPagesRepository
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class BookmarksScreenViewModel : BaseViewModel() {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)
  private val catalogPagesRepository: CatalogPagesRepository by inject(CatalogPagesRepository::class.java)
  private val sortBookmarks: SortBookmarks by inject(SortBookmarks::class.java)

  private val _bookmarksList = mutableStateListOf<ThreadBookmarkUi>()
  val bookmarksList: List<ThreadBookmarkUi>
    get() = _bookmarksList

  private val _canUseFancyAnimations = mutableStateOf(false)
  val canUseFancyAnimations: State<Boolean>
    get() = _canUseFancyAnimations

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

      val loadedBookmarks = sortBookmarks.await(bookmarksManager.getAllBookmarks())
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

        _bookmarksList.addAll(0, newBookmarks)
      }
      is BookmarksManager.Event.Deleted -> {
        val bookmarksToDelete = event.threadDescriptors.toSet()

        _bookmarksList.mutableIteration { iterator, threadBookmarkUi ->
          if (threadBookmarkUi.threadDescriptor in bookmarksToDelete) {
            iterator.remove()
          }

          return@mutableIteration true
        }
      }
      is BookmarksManager.Event.Updated -> {
        event.threadDescriptors.forEach { threadDescriptor ->
          val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
            ?: return@forEach

          val index = _bookmarksList.indexOfFirst { threadBookmarkUi ->
            threadBookmarkUi.threadDescriptor == threadDescriptor
          }

          if (index >= 0) {
            val threadPage = catalogPagesRepository.getThreadPage(threadDescriptor)

            _bookmarksList[index].updateStatsFrom(
              threadBookmark = threadBookmark,
              threadPage = threadPage
            )
          }
        }
      }
    }
  }

}