package com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repoository.CatalogPagesRepository
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

  fun matchesQuery(searchQuery: String): Boolean {
    return title.contains(other = searchQuery, ignoreCase = true)
  }

  fun updateStatsFrom(
    threadBookmark: ThreadBookmark,
    threadPage: CatalogPagesRepository.ThreadPage?
  ) {
    threadBookmarkStatsUi.updateFrom(
      threadBookmark = threadBookmark,
      threadPage = threadPage
    )
  }

  fun updatePagesFrom(
    threadPage: CatalogPagesRepository.ThreadPage
  ) {
    threadBookmarkStatsUi.updatePagesFrom(threadPage)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadBookmarkUi

    if (threadDescriptor != other.threadDescriptor) return false
    if (title != other.title) return false
    if (thumbnailUrl != other.thumbnailUrl) return false
    if (threadBookmarkStatsUi != other.threadBookmarkStatsUi) return false
    if (createdOn != other.createdOn) return false

    return true
  }

  override fun hashCode(): Int {
    var result = threadDescriptor.hashCode()
    result = 31 * result + title.hashCode()
    result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
    result = 31 * result + threadBookmarkStatsUi.hashCode()
    result = 31 * result + createdOn.hashCode()
    return result
  }

  companion object {
    fun fromThreadBookmark(
      threadBookmark: ThreadBookmark,
      threadPage: CatalogPagesRepository.ThreadPage?
    ): ThreadBookmarkUi? {
      return ThreadBookmarkUi(
        threadDescriptor = threadBookmark.threadDescriptor,
        title = threadBookmark.title ?: return null,
        thumbnailUrl = threadBookmark.thumbnailUrl?.toString(),
        createdOn = threadBookmark.createdOn,
        threadBookmarkStatsUi = ThreadBookmarkStatsUi.fromThreadBookmark(
          threadBookmark = threadBookmark,
          currentPage = threadPage?.page,
          totalPages = threadPage?.totalPages
        )
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

  fun hasPagesInfo(): Boolean {
    return totalPages.value > 0
  }

  fun isDead(): Boolean = isDeleted.value || isArchived.value

  fun isDeadOrNotWatching(): Boolean = isDead() || !watching.value

  fun updateFrom(threadBookmark: ThreadBookmark, threadPage: CatalogPagesRepository.ThreadPage?) {
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

    _currentPage.value = threadPage?.page ?: 0
    _totalPages.value = threadPage?.totalPages ?: 0
  }

  fun updatePagesFrom(threadPage: CatalogPagesRepository.ThreadPage) {
    _currentPage.value = threadPage.page
    _totalPages.value = threadPage.totalPages
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadBookmarkStatsUi

    if (_watching.value != other._watching.value) return false
    if (_newPosts.value != other._newPosts.value) return false
    if (_newQuotes.value != other._newQuotes.value) return false
    if (_totalPosts.value != other._totalPosts.value) return false
    if (_currentPage.value != other._currentPage.value) return false
    if (_totalPages.value != other._totalPages.value) return false
    if (_isBumpLimit.value != other._isBumpLimit.value) return false
    if (_isImageLimit.value != other._isImageLimit.value) return false
    if (_isFirstFetch.value != other._isFirstFetch.value) return false
    if (_isDeleted.value != other._isDeleted.value) return false
    if (_isArchived.value != other._isArchived.value) return false
    if (_isError.value != other._isError.value) return false

    return true
  }

  override fun hashCode(): Int {
    var result = _watching.value.hashCode()
    result = 31 * result + _newPosts.value.hashCode()
    result = 31 * result + _newQuotes.value.hashCode()
    result = 31 * result + _totalPosts.value.hashCode()
    result = 31 * result + _currentPage.value.hashCode()
    result = 31 * result + _totalPages.value.hashCode()
    result = 31 * result + _isBumpLimit.value.hashCode()
    result = 31 * result + _isImageLimit.value.hashCode()
    result = 31 * result + _isFirstFetch.value.hashCode()
    result = 31 * result + _isDeleted.value.hashCode()
    result = 31 * result + _isArchived.value.hashCode()
    result = 31 * result + _isError.value.hashCode()
    return result
  }


  companion object {
    fun fromThreadBookmark(
      threadBookmark: ThreadBookmark,
      currentPage: Int?,
      totalPages: Int?
    ): ThreadBookmarkStatsUi {
      return ThreadBookmarkStatsUi(
        watching = threadBookmark.isActive(),
        newPosts = threadBookmark.newPostsCount(),
        newQuotes = threadBookmark.newQuotesCount(),
        totalPosts = threadBookmark.totalPostsCount,
        currentPage = currentPage ?: 0,
        totalPages = totalPages ?: 0,
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