package com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import org.joda.time.DateTime

@Stable
class ThreadBookmarkUi(
  val threadDescriptor: ThreadDescriptor,
  val title: String,
  val thumbnailUrl: String?,
  val threadBookmarkStatsUi: ThreadBookmarkStatsUi,
  val createdOn: DateTime,
) {
  private val _selected = mutableStateOf(false)
  val selected: State<Boolean>
    get() = _selected
  private val _highlighted = mutableStateOf(false)
  val highlighted: State<Boolean>
    get() = _highlighted

  fun updateStatsFrom(
    threadBookmark: ThreadBookmark,
    currentPage: Int?,
    totalPages: Int?
  ) {
    threadBookmarkStatsUi.updateFrom(
      threadBookmark = threadBookmark,
      currentPage = currentPage,
      totalPages = totalPages
    )
  }

  companion object {
    fun fromThreadBookmark(threadBookmark: ThreadBookmark): ThreadBookmarkUi? {
      return ThreadBookmarkUi(
        threadDescriptor = threadBookmark.threadDescriptor,
        title = threadBookmark.title ?: return null,
        thumbnailUrl = threadBookmark.thumbnailUrl?.toString(),
        createdOn = threadBookmark.createdOn,
        threadBookmarkStatsUi = ThreadBookmarkStatsUi.fromThreadBookmark(threadBookmark)
      )
    }
  }

}

@Stable
class ThreadBookmarkStatsUi private constructor(
  watching: Boolean,
  newPosts: Int = 0,
  newQuotes: Int = 0,
  totalPosts: Int = 0,
  currentPage: Int = 0,
  totalPages: Int = 0,
  isBumpLimit: Boolean = false,
  isImageLimit: Boolean = false,
  isFirstFetch: Boolean = false,
  isDeleted: Boolean = false,
  isArchived: Boolean = false,
  isError: Boolean = false
) {
  private val _watching = mutableStateOf(watching)
  val watching: State<Boolean>
    get() = _watching
  private val _newPosts = mutableStateOf(newPosts)
  val newPosts: State<Int>
    get() = _newPosts
  private val _newQuotes = mutableStateOf(newQuotes)
  val newQuotes: State<Int>
    get() = _newQuotes
  private val _totalPosts = mutableStateOf(totalPosts)
  val totalPosts: State<Int>
    get() = _totalPosts
  private val _currentPage = mutableStateOf(currentPage)
  val currentPage: State<Int>
    get() = _currentPage
  private val _totalPages = mutableStateOf(totalPages)
  val totalPages: State<Int>
    get() = _totalPages
  private val _isBumpLimit = mutableStateOf(isBumpLimit)
  val isBumpLimit: State<Boolean>
    get() = _isBumpLimit
  private val _isImageLimit = mutableStateOf(isImageLimit)
  val isImageLimit: State<Boolean>
    get() = _isImageLimit
  private val _isFirstFetch = mutableStateOf(isFirstFetch)
  val isFirstFetch: State<Boolean>
    get() = _isFirstFetch
  private val _isDeleted = mutableStateOf(isDeleted)
  val isDeleted: State<Boolean>
    get() = _isDeleted
  private val _isArchived = mutableStateOf(isArchived)
  val isArchived: State<Boolean>
    get() = _isArchived
  private val _isError = mutableStateOf(isError)
  val isError: State<Boolean>
    get() = _isError

  fun isLastPage(): Boolean {
    val pagesCount = totalPages.value
    val page = currentPage.value

    return pagesCount in 1..page
  }

  fun isDead(): Boolean = isDeleted.value || isArchived.value

  fun isDeadOrNotWatching(): Boolean = isDead() || !watching.value

  fun updateFrom(threadBookmark: ThreadBookmark, currentPage: Int?, totalPages: Int?) {
    _watching.value = threadBookmark.isActive()
    _newPosts.value = threadBookmark.newPostsCount()
    _newQuotes.value = threadBookmark.newQuotesCount()
    _totalPosts.value = threadBookmark.totalPostsCount
    _isBumpLimit.value = threadBookmark.isBumpLimit()
    _isImageLimit.value = threadBookmark.isImageLimit()
    _isFirstFetch.value = threadBookmark.isFirstFetch()
    _isDeleted.value = threadBookmark.isThreadDeleted()
    _isArchived.value = threadBookmark.isThreadArchived()
    _isError.value = threadBookmark.isError()

    _currentPage.value = currentPage ?: 0
    _totalPages.value = totalPages ?: 0
  }

  companion object {
    fun fromThreadBookmark(threadBookmark: ThreadBookmark): ThreadBookmarkStatsUi {
      return ThreadBookmarkStatsUi(
        watching = threadBookmark.isActive(),
        newPosts = threadBookmark.newPostsCount(),
        newQuotes = threadBookmark.newQuotesCount(),
        totalPosts = threadBookmark.totalPostsCount,
        isBumpLimit = threadBookmark.isBumpLimit(),
        isImageLimit = threadBookmark.isImageLimit(),
        isFirstFetch = threadBookmark.isFirstFetch(),
        isDeleted = threadBookmark.isThreadDeleted(),
        isArchived = threadBookmark.isThreadArchived(),
        isError = threadBookmark.isError(),
      )
    }
  }

}