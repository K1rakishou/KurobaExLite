package com.github.k1rakishou.kurobaexlite.features.posts.search.image

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.settings.RemoteImageSearchSettings
import com.github.k1rakishou.kurobaexlite.interactors.image_search.YandexImageSearch
import com.github.k1rakishou.kurobaexlite.model.FirewallDetectedException
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.logcat

class RemoteImageSearchScreenViewModel(
  private val remoteImageSearchSettings: RemoteImageSearchSettings,
  private val yandexImageSearch: YandexImageSearch
) : BaseViewModel() {

  private val _lastUsedSearchInstance = mutableStateOf<ImageSearchInstanceType?>(null)
  val lastUsedSearchInstance: State<ImageSearchInstanceType?>
    get() = _lastUsedSearchInstance

  private val _searchInstances = mutableStateMapOf<ImageSearchInstanceType, ImageSearchInstance>()
  val searchInstances: Map<ImageSearchInstanceType, ImageSearchInstance>
    get() = _searchInstances

  private val _searchResults = mutableStateMapOf<ImageSearchInstanceType, AsyncData<ImageResults>>()
  val searchResults: Map<ImageSearchInstanceType, AsyncData<ImageResults>>
    get() = _searchResults

  private val _solvingCaptcha = MutableStateFlow<String?>(null)
  val solvingCaptcha: StateFlow<String?>
    get() = _solvingCaptcha.asStateFlow()

  var searchQuery = mutableStateOf("")
  val searchErrorToastFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

  private var activeSearchJob: Job? = null

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    val instances = ImageSearchInstance.createAll(remoteImageSearchSettings)
    instances.forEach { imageSearchInstance -> imageSearchInstance.init() }

    Snapshot.withMutableSnapshot {
      instances.forEach { imageSearchInstance ->
        _searchInstances[imageSearchInstance.type] = imageSearchInstance
      }

      _lastUsedSearchInstance.value = ImageSearchInstanceType.Yandex
      searchQuery.value = ""
    }
  }

  override fun onCleared() {
    super.onCleared()

    cleanup()
  }

  fun updateYandexSmartCaptchaCookies(newCookies: String) {
    viewModelScope.launch {
      val imageSearchInstance = getCurrentSearchInstance()
        ?: return@launch

      imageSearchInstance.updateCookies(newCookies)
    }
  }

  fun finishedSolvingCaptcha() {
    _solvingCaptcha.value = null
  }

  fun changeSearchInstance(newImageSearchInstanceType: ImageSearchInstanceType) {
    _lastUsedSearchInstance.value = newImageSearchInstanceType

    val prevQuery = getCurrentSearchInstance()?.searchQuery
    val newQuery = searchQuery.value

    val searchResults = _searchResults[newImageSearchInstanceType]
    if ((searchResults !is AsyncData.Data || prevQuery != newQuery) && newQuery.isNotEmpty()) {
      onSearchQueryChanged(newQuery)
    }
  }

  fun updatePrevLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    imageSearchInstance.updateLazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset)
  }

  fun cleanup() {
    activeSearchJob?.cancel()
    activeSearchJob = null
  }

  fun reload() {
    getCurrentSearchInstance()?.let { imageSearchInstance ->
      imageSearchInstance.updateCurrentPage(0)
      imageSearchInstance.updateLazyListState(0, 0)
    }

    onSearchQueryChanged(searchQuery.value)
  }

  fun reloadCurrentPage() {
    doSearchInternal(searchQuery.value)
  }

  fun onSearchQueryChanged(newQuery: String) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    _searchResults[imageSearchInstance.type] = AsyncData.Loading
    imageSearchInstance.updateCurrentPage(0)
    imageSearchInstance.updateLazyListState(
      firstVisibleItemIndex = 0,
      firstVisibleItemScrollOffset = 0
    )

    doSearchInternal(
      query = newQuery,
      debounce = true
    )
  }

  fun onNewPageRequested(page: Int) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    imageSearchInstance.updateCurrentPage(page)

    doSearchInternal(searchQuery.value)
  }

  private fun doSearchInternal(query: String, debounce: Boolean = false) {
    activeSearchJob?.cancel()
    activeSearchJob = null

    activeSearchJob = mainScope.launch {
      if (debounce) {
        delay(1000L)
      }

      if (_solvingCaptcha.value != null) {
        return@launch
      }

      val currentImageSearchInstance = getCurrentSearchInstance()
        ?: return@launch

      currentImageSearchInstance.updateSearchQuery(query)

      if (query.isEmpty()) {
        _searchResults[currentImageSearchInstance.type] = AsyncData.Uninitialized
        return@launch
      }

      val searchUrl = currentImageSearchInstance.buildSearchUrl(
        query = query,
        page = currentImageSearchInstance.currentPage
      )

      val hasCookies = currentImageSearchInstance.cookies.isNotNullNorEmpty()

      logcat(TAG) {
        "search() query=\'$query\', page=${currentImageSearchInstance.currentPage}, " +
          "hasCookies=${hasCookies}, searchUrl=${searchUrl}"
      }

      val foundImagesResult = when (currentImageSearchInstance.type) {
        ImageSearchInstanceType.Yandex -> {
          yandexImageSearch.await(searchUrl, currentImageSearchInstance.cookies)
        }
      }

      val newFoundImages = if (foundImagesResult.isFailure) {
        val error = foundImagesResult.exceptionOrThrow()
        logcatError(TAG) { "search() error: ${error.asLogIfImportantOrErrorMessage()}" }

        if (error is FirewallDetectedException && error.firewallType == FirewallType.YandexSmartCaptcha) {
          _solvingCaptcha.emit(error.requestUrl.toString())
          return@launch
        }

        _searchResults[currentImageSearchInstance.type] = AsyncData.Error(error)
        return@launch
      } else {
        foundImagesResult.getOrThrow()
          .map { imageSearchResult -> ImageSearchResultUi.fromImageSearchResult(imageSearchResult) }
      }

      logcat(TAG) { "search() got ${newFoundImages.size} results" }

      val prevImageResults = (_searchResults[currentImageSearchInstance.type] as? AsyncData.Data)?.data
      _searchResults[currentImageSearchInstance.type] = when {
        prevImageResults == null -> {
          AsyncData.Data(ImageResults(newFoundImages))
        }
        newFoundImages.isNotEmpty() -> {
          AsyncData.Data(prevImageResults.append(newFoundImages))
        }
        else -> {
          AsyncData.Data(prevImageResults.endReached())
        }
      }
    }
  }

  private fun getCurrentSearchInstance(): ImageSearchInstance? {
    val imageSearchInstanceType = _lastUsedSearchInstance.value
      ?: return null

    return _searchInstances[imageSearchInstanceType]
  }

  class ImageResults(
    val results: List<ImageSearchResultUi>,
    val endReached: Boolean = false
  ) {

    fun append(newFoundImages: List<ImageSearchResultUi>): ImageResults {
      return ImageResults(
        results = results + newFoundImages,
        endReached = endReached
      )
    }

    fun endReached(): ImageResults {
      return ImageResults(
        results = results,
        endReached = true
      )
    }

  }

  companion object {
    private const val TAG = "ImageSearchControllerViewModel"
  }

}