package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.sort.CatalogThreadSorter
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.state.PostsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat

class CatalogScreenViewModel(
  application: KurobaExLiteApplication,
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(application, savedStateHandle) {
  private val screenKey: ScreenKey = CatalogScreen.SCREEN_KEY
  private val catalogScreenState = PostScreenState()
  private var loadCatalogJob: Job? = null

  override val postScreenState: PostScreenState = catalogScreenState

  val catalogDescriptor: CatalogDescriptor?
    get() = chanDescriptor as? CatalogDescriptor

  override suspend fun onViewModelReady() {
    val prevCatalogDescriptor = savedStateHandle.get<CatalogDescriptor>(PREV_CATALOG_DESCRIPTOR)
    logcat(tag = TAG) { "onViewModelReady() prevCatalogDescriptor=${prevCatalogDescriptor}" }

    if (prevCatalogDescriptor != null) {
      loadCatalog(
        catalogDescriptor = prevCatalogDescriptor,
        loadOptions = LoadOptions(forced = true),
      )
    } else {
      // TODO(KurobaEx): remove me once last visited catalog is remembered
      loadCatalog(
        catalogDescriptor = CatalogDescriptor(Chan4.SITE_KEY, "g"),
        loadOptions = LoadOptions(forced = true),
      )
    }
  }

  override fun reload(
    loadOptions: LoadOptions,
    onReloadFinished: (() -> Unit)?
  ) {
    val currentlyOpenedCatalog = chanThreadManager.currentlyOpenedCatalog
    if (currentlyOpenedCatalog != null) {
      resetPosition(currentlyOpenedCatalog)
    }

    loadCatalog(
      catalogDescriptor = currentlyOpenedCatalog,
      loadOptions = loadOptions.copy(forced = true),
      onReloadFinished = onReloadFinished,
    )
  }

  override fun refresh(onRefreshFinished: (() -> Unit)?) {
    error("Refreshing catalogs is not supported")
  }

  fun loadCatalog(
    catalogDescriptor: CatalogDescriptor?,
    loadOptions: LoadOptions = LoadOptions(),
    onReloadFinished: (() -> Unit)? = null
  ) {
    loadCatalogJob?.cancel()
    loadCatalogJob = null

    loadCatalogJob = viewModelScope.launch {
      loadCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        loadOptions = loadOptions,
        onReloadFinished = onReloadFinished
      )
    }
  }

  private suspend fun loadCatalogInternal(
    catalogDescriptor: CatalogDescriptor?,
    loadOptions: LoadOptions,
    onReloadFinished: (() -> Unit)?
  ) {
    if (!loadOptions.forced && chanThreadManager.currentlyOpenedCatalog == catalogDescriptor) {
      onReloadFinished?.invoke()
      return
    }

    val startTime = SystemClock.elapsedRealtime()
    onCatalogLoadingStart(catalogDescriptor, loadOptions)

    val postsLoadResultMaybe = if (loadOptions.loadFromNetwork || catalogDescriptor == null) {
      if (loadOptions.deleteCached && catalogDescriptor != null) {
        chanThreadManager.delete(catalogDescriptor)
      }

      chanThreadManager.loadCatalog(catalogDescriptor)
    } else {
      val catalogThreads = chanCache.getCatalogThreads(catalogDescriptor)
      Result.success(PostsLoadResult(updatedPosts = catalogThreads, newPosts = emptyList()))
    }

    if (postsLoadResultMaybe.isFailure) {
      val error = postsLoadResultMaybe.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val postsLoadResult = postsLoadResultMaybe.unwrap()
    if (postsLoadResult == null || catalogDescriptor == null) {
      catalogScreenState.postsAsyncDataState.value = AsyncData.Empty
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    if (postsLoadResult.isEmpty()) {
      val error = CatalogDisplayException("Catalog /${catalogDescriptor}/ has no posts")

      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val catalogSortSetting = appSettings.catalogSort.read()
    val sortedThreads = CatalogThreadSorter.sortCatalogPostData(
      catalogThreads = postsLoadResult.allCombined(),
      catalogSortSetting = catalogSortSetting
    )

    val initialParsedPosts = parseInitialBatchOfPosts(
      startPostDescriptor = null,
      chanDescriptor = catalogDescriptor,
      postDataList = sortedThreads,
      isCatalogMode = true
    )

    val catalogThreadsState = PostsState(catalogDescriptor)
    catalogThreadsState.insertOrUpdateMany(initialParsedPosts)

    catalogScreenState.postsAsyncDataState.value = AsyncData.Data(catalogThreadsState)
    postListBuilt?.await()
    restoreScrollPosition(catalogDescriptor)

    parseRemainingPostsAsync(
      chanDescriptor = catalogDescriptor,
      postDataList = sortedThreads,
      sorter = { postCellData ->
        CatalogThreadSorter.sortCatalogPostCellData(
          catalogThreads = postCellData,
          catalogSortSetting = catalogSortSetting
        )
      },
      onStartParsingPosts = {
        snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
          postsCount = sortedThreads.size,
          screenKey = screenKey
        )
      },
      onPostsParsed = { postCellDataList ->
        logcat {
          "loadCatalog($catalogDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
            "catalogThreads=${sortedThreads.size}"
        }

        try {
          catalogThreadsState.insertOrUpdateMany(postCellDataList)
          onCatalogLoadingEnd(catalogDescriptor)
        } finally {
          withContext(NonCancellable + Dispatchers.Main) {
            snackbarManager.popCatalogOrThreadPostsLoadingSnackbar()
            onReloadFinished?.invoke()
          }
        }
      }
    )
  }

  fun onCatalogSortChanged() {
    viewModelScope.launch {
      val catalogDescriptor = chanDescriptor as? CatalogDescriptor
        ?: return@launch

      loadCatalog(
        catalogDescriptor = catalogDescriptor,
        loadOptions = LoadOptions(
          showLoadingIndicator = false,
          forced = true,
          loadFromNetwork = false
        ),
        onReloadFinished = { scrollTop() }
      )
    }
  }

  class CatalogDisplayException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "CatalogScreenViewModel"

    const val PREV_CATALOG_DESCRIPTOR = "prev_catalog_descriptor"
  }

}