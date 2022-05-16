package com.github.k1rakishou.kurobaexlite.features.posts.search

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.interactors.catalog.CatalogGlobalSearch
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class GlobalSearchScreenViewModel : BaseViewModel() {
  private val catalogGlobalSearch: CatalogGlobalSearch by inject(CatalogGlobalSearch::class.java)

  private val screenState = ScreenState()

  val postsAsyncState: State<AsyncData<List<PostCellData>>> = screenState.postsAsyncState
  val searchQueryState: State<String?> = screenState.searchQueryState
  val endReachedState: State<Boolean> = screenState.endReachedState
  val currentPageState: State<Int> = screenState.currentPageState
  val currentLoadingPageState: State<Int?> = screenState.currentLoadingPageState
  val pageLoadErrorState: State<Throwable?> = screenState.pageLoadErrorState

  private var searchJob: Job? = null

  fun fullReload() {
    searchJob?.cancel()

    searchJob = viewModelScope.launch {
      try {
        val searchQuery = screenState.searchQuery?.takeIf { it.isNotEmpty() }
          ?: return@launch
        val catalogDescriptor = screenState.catalogDescriptor
          ?: return@launch

        screenState.fullReload()

        logcat(TAG) { "fullReload()" }
        loadPageInternal(catalogDescriptor, searchQuery)
      } finally {
        searchJob = null
      }
    }
  }

  fun updateSearchQuery(searchQuery: String?, catalogDescriptor: CatalogDescriptor) {
    searchJob?.cancel()

    searchJob = viewModelScope.launch {
      try {
        delay(250L)

        if (!screenState.updateSearchParams(searchQuery, catalogDescriptor)) {
          return@launch
        }

        if (searchQuery.isNullOrEmpty()) {
          return@launch
        }

        logcat(TAG) { "updateSearchQuery() searchQuery=${searchQuery}" }
        loadPageInternal(catalogDescriptor, searchQuery)
      } finally {
        searchJob = null
      }
    }
  }

  fun onNextPageRequested(currentPage: Int, currentLoadingPage: Int?) {
    if (searchJob != null) {
      return
    }

    searchJob = viewModelScope.launch {
      try {
        val searchQuery = screenState.searchQueryState.value
          ?: return@launch
        val currentCatalogDescriptor = screenState.currentCatalogDescriptorState.value
          ?: return@launch

        logcat(TAG) { "onNextPageRequested() currentPage=${currentPage}, currentLoadingPage=${currentLoadingPage}" }
        loadPageInternal(currentCatalogDescriptor, searchQuery)
      } finally {
        searchJob = null
      }
    }
  }

  fun reloadCurrentPage(currentPage: Int) {
    if (searchJob != null) {
      return
    }

    searchJob = viewModelScope.launch {
      try {
        val searchQuery = screenState.searchQueryState.value
          ?: return@launch
        val currentCatalogDescriptor = screenState.currentCatalogDescriptorState.value
          ?: return@launch

        if (screenState.searchQuery != searchQuery) {
          logcat(TAG) {
            "reloadCurrentPage() Skipping request with searchQuery: ${searchQuery} because the actual " +
              "searchQuery was changed to ${screenState.searchQuery}"
          }

          return@launch
        }

        logcat(TAG) { "reloadCurrentPage() currentPage=${currentPage}" }

        val isSiteWideSearch = screenState.isSiteWideSearch
        screenState.onPageLoadError(null)
        screenState.onPageReloadingStarted()

        val foundPostsResult = catalogGlobalSearch.await(
          catalogDescriptor = currentCatalogDescriptor,
          isSiteWideSearch = isSiteWideSearch,
          searchQuery = searchQuery,
          page = currentPage
        )

        foundPostsResult
          .onFailure { error -> screenState.onPageLoadError(error) }
          .onSuccess { parsedPosts -> screenState.onPageLoadSuccess(parsedPosts) }
      } finally {
        searchJob = null
      }
    }
  }

  private suspend fun loadPageInternal(
    catalogDescriptor: CatalogDescriptor,
    searchQuery: String?
  ) {
    if (!screenState.onLoadingStarted()) {
      return
    }

    screenState.onPageLoadError(null)

    if (searchQuery.isNullOrEmpty()) {
      return
    }

    val isSiteWideSearch = screenState.isSiteWideSearch
    val currentPage = screenState.currentPage

    logcat(TAG) {
      "loadPageInternal() " +
        "catalogDescriptor=${catalogDescriptor}, " +
        "searchQuery=${searchQuery}, " +
        "currentPage=${currentPage}, " +
        "isSiteWideSearch=${isSiteWideSearch}"
    }

    val foundPostsResult = catalogGlobalSearch.await(
      catalogDescriptor = catalogDescriptor,
      isSiteWideSearch = isSiteWideSearch,
      searchQuery = searchQuery,
      page = currentPage
    )

    if (screenState.searchQuery != searchQuery) {
      logcat(TAG) {
        "loadPageInternal() Skipping request with searchQuery: ${searchQuery} because the actual " +
          "searchQuery was changed to ${screenState.searchQuery}"
      }

      return
    }

    foundPostsResult
      .onFailure { error -> screenState.onPageLoadError(error) }
      .onSuccess { parsedPosts -> screenState.onPageLoadSuccess(parsedPosts) }
  }

  class ScreenState {
    private val duplicateChecker = mutableSetWithCap<PostDescriptor>(128)

    private val _postsAsyncState = mutableStateOf<AsyncData<SnapshotStateList<PostCellData>>>(AsyncData.Uninitialized)
    val postsAsyncState: State<AsyncData<List<PostCellData>>>
      get() = _postsAsyncState

    private val _searchQueryState = mutableStateOf<String?>(null)
    val searchQueryState: State<String?>
      get() = _searchQueryState

    private val _currentCatalogDescriptorState = mutableStateOf<CatalogDescriptor?>(null)
    val currentCatalogDescriptorState: State<CatalogDescriptor?>
      get() = _currentCatalogDescriptorState

    private val _pageLoadErrorState = mutableStateOf<Throwable?>(null)
    val pageLoadErrorState: State<Throwable?>
      get() = _pageLoadErrorState

    private val _currentPageState = mutableStateOf(0)
    val currentPageState: State<Int>
      get() = _currentPageState

    private val _currentLoadingPageState = mutableStateOf<Int?>(null)
    val currentLoadingPageState: State<Int?>
      get() = _currentLoadingPageState

    private val _isSiteWideSearchState = mutableStateOf(false)
    val isSiteWideSearchState: State<Boolean>
      get() = _isSiteWideSearchState

    private val _endReachedState = mutableStateOf(false)
    val endReachedState: State<Boolean>
      get() = _endReachedState

    private val postsAsync: AsyncData<SnapshotStateList<PostCellData>>
      get() = _postsAsyncState.value

    val currentPage: Int
      get() = currentPageState.value
    val isSiteWideSearch: Boolean
      get() = isSiteWideSearchState.value
    val searchQuery: String?
      get() = _searchQueryState.value
    val catalogDescriptor: CatalogDescriptor?
      get() = _currentCatalogDescriptorState.value

    fun fullReload() {
      _postsAsyncState.value = AsyncData.Loading
      _pageLoadErrorState.value = null
      _currentPageState.value = 0
      _currentLoadingPageState.value = null
      _endReachedState.value = false
      duplicateChecker.clear()
    }

    fun updateSearchParams(
      searchQuery: String?,
      catalogDescriptor: CatalogDescriptor
    ): Boolean {
      if (_searchQueryState.value == searchQuery && _currentCatalogDescriptorState.value == catalogDescriptor) {
        return false
      }

      _searchQueryState.value = searchQuery
      _currentCatalogDescriptorState.value = catalogDescriptor

      _currentPageState.value = 0
      _currentLoadingPageState.value = null
      _endReachedState.value = false

      _postsAsyncState.value = AsyncData.Loading
      duplicateChecker.clear()

      return true
    }

    fun onLoadingStarted(): Boolean {
      if (_currentLoadingPageState.value == _currentPageState.value) {
        return false
      }

      if (postsAsync !is AsyncData.Data) {
        _postsAsyncState.value = AsyncData.Loading
        duplicateChecker.clear()
      }

      _currentLoadingPageState.value = _currentPageState.value
      return true
    }

    fun onPageReloadingStarted() {
      if (postsAsync !is AsyncData.Data) {
        _postsAsyncState.value = AsyncData.Loading
        duplicateChecker.clear()
      }

      _currentLoadingPageState.value = _currentPageState.value
    }

    fun onPageLoadError(error: Throwable?) {
      _pageLoadErrorState.value = error
    }

    fun onPageLoadSuccess(newPosts: List<PostCellData>) {
      val postsListMutable = if (postsAsync !is AsyncData.Data) {
        val newPostList = mutableStateListOf<PostCellData>()
        val newPostListAsync = AsyncData.Data(newPostList)
        _postsAsyncState.value = newPostListAsync

        newPostList
      } else {
        (postsAsync as AsyncData.Data).data
      }

      newPosts.forEach { newPost ->
        if (!duplicateChecker.add(newPost.postDescriptor)) {
          return@forEach
        }

        postsListMutable += newPost
      }

      if (newPosts.isEmpty()) {
        _endReachedState.value = true
        return
      }

      _currentPageState.value += 1
    }

  }

  companion object {
    private const val TAG = "GlobalSearchScreenViewModel"
  }

}