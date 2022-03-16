package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat

class CatalogScreenViewModel(
  private val chanThreadManager: ChanThreadManager,
  private val chanCache: ChanCache,
  application: KurobaExLiteApplication,
  globalConstants: GlobalConstants,
  themeEngine: ThemeEngine,
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(application, globalConstants, themeEngine, savedStateHandle) {
  private val screenKey: ScreenKey = CatalogScreen.SCREEN_KEY
  private val catalogScreenState = CatalogScreenState()
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

  override fun refresh() {
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

    val catalogDataResult = if (loadOptions.loadFromNetwork || catalogDescriptor == null) {
      chanThreadManager.loadCatalog(catalogDescriptor)
    } else {
      val catalogThreads = chanCache.getCatalogThreads(catalogDescriptor)
      val catalogData = CatalogData(catalogDescriptor, catalogThreads)
      Result.success(catalogData)
    }

    if (catalogDataResult.isFailure) {
      val error = catalogDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val catalogData = catalogDataResult.unwrap()
    if (catalogData == null || catalogDescriptor == null) {
      catalogScreenState.postsAsyncDataState.value = AsyncData.Empty
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    if (catalogData.catalogThreads.isEmpty()) {
      val error = CatalogDisplayException("Catalog /${catalogDescriptor}/ has no posts")

      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val catalogSortSetting = appSettings.catalogSort.read()
    val sortedThreads = CatalogThreadSorter.sortCatalogThreads(
      catalogThreads = catalogData.catalogThreads,
      catalogSortSetting = catalogSortSetting
    )

    val sortedCatalogData = CatalogData(catalogDescriptor, sortedThreads)

    parsePostsAround(
      startPostDescriptor = null,
      chanDescriptor = catalogDescriptor,
      postDataList = sortedCatalogData.catalogThreads,
      isCatalogMode = true
    )

    val catalogThreadsState = CatalogThreadsState(
      catalogDescriptor = catalogDescriptor,
      catalogThreads = sortedCatalogData.catalogThreads
    )

    catalogScreenState.postsAsyncDataState.value = AsyncData.Data(catalogThreadsState)
    postListBuilt?.await()
    restoreScrollPosition(catalogDescriptor)

    parseRemainingPostsAsync(
      chanDescriptor = catalogDescriptor,
      postDataList = sortedCatalogData.catalogThreads,
      onStartParsingPosts = {
        snackbarManager.pushCatalogOrThreadPostsLoadingSnackbar(
          postsCount = sortedCatalogData.catalogThreads.size,
          screenKey = screenKey
        )
      },
      onPostsParsed = { postDataList ->
        logcat {
          "loadCatalog($catalogDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
            "catalogThreads=${sortedCatalogData.catalogThreads.size}"
        }

        try {
          chanCache.insertCatalogThreads(catalogDescriptor, catalogData.catalogThreads)
          onCatalogLoadingEnd(catalogDescriptor)

          _postsFullyParsedOnceFlow.emit(true)
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