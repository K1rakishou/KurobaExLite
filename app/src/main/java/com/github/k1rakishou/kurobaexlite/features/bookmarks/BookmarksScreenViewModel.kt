package com.github.k1rakishou.kurobaexlite.features.bookmarks

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.mutableIteration
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class BookmarksScreenViewModel : BaseViewModel() {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)

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
      bookmarksManager.awaitUntilInitialized()

      val loadedBookmarks = bookmarksManager.getAllBookmarks()
        .mapNotNull { threadBookmark -> ThreadBookmarkUi.fromThreadBookmark(threadBookmark) }

      _bookmarksList.clear()
      _bookmarksList.addAll(loadedBookmarks)

      bookmarksManager.bookmarkEventsFlow.collect { event ->
        when (event) {
          is BookmarksManager.Event.Created -> {
            val newBookmarks = event.threadDescriptors.mapNotNull { threadDescriptor ->
              val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
                ?: return@mapNotNull null

              return@mapNotNull ThreadBookmarkUi.fromThreadBookmark(threadBookmark)
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
                _bookmarksList[index].updateStatsFrom(
                  threadBookmark = threadBookmark,
                  currentPage = null,
                  totalPages = null
                )
              }
            }
          }
        }
      }
    }
  }

}