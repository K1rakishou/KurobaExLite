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
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat

class CatalogScreenViewModel(
  private val chanThreadManager: ChanThreadManager,
  private val chanThreadCache: ChanThreadCache,
  application: KurobaExLiteApplication,
  globalConstants: GlobalConstants,
  themeEngine: ThemeEngine,
  savedStateHandle: SavedStateHandle
) : PostScreenViewModel(application, globalConstants, themeEngine, savedStateHandle) {
  private val catalogScreenState = CatalogScreenState()
  private var loadCatalogJob: Job? = null

  override val postScreenState: PostScreenState = catalogScreenState

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
      try {
        loadCatalogInternal(
          catalogDescriptor = catalogDescriptor,
          loadOptions = loadOptions
        )
      } finally {
        onReloadFinished?.invoke()
      }
    }
  }

  private suspend fun loadCatalogInternal(
    catalogDescriptor: CatalogDescriptor?,
    loadOptions: LoadOptions
  ) {
    if (!loadOptions.forced && chanThreadManager.currentlyOpenedCatalog == catalogDescriptor) {
      return
    }

    onLoadingCatalog()

    postListBuilt = CompletableDeferred()
    val startTime = SystemClock.elapsedRealtime()

    _postsFullyParsedOnceFlow.emit(false)

    if (loadOptions.showLoadingIndicator) {
      catalogScreenState.postsAsyncDataState.value = AsyncData.Loading
    }

    savedStateHandle.set(PREV_CATALOG_DESCRIPTOR, catalogDescriptor)

    val catalogDataResult = chanThreadManager.loadCatalog(catalogDescriptor)
    if (catalogDataResult.isFailure) {
      val error = catalogDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      return
    }

    val catalogData = catalogDataResult.unwrap()
    if (catalogData == null || catalogDescriptor == null) {
      catalogScreenState.postsAsyncDataState.value = AsyncData.Empty
      _postsFullyParsedOnceFlow.emit(true)
      return
    }

    if (catalogData.catalogThreads.isEmpty()) {
      val error = CatalogDisplayException("Catalog /${catalogDescriptor}/ has no posts")

      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      _postsFullyParsedOnceFlow.emit(true)
      return
    }

    parsePostsAround(
      startIndex = 0,
      chanDescriptor = catalogDescriptor,
      postDataList = catalogData.catalogThreads,
      isCatalogMode = true
    )

    val catalogThreadsState = CatalogThreadsState(
      catalogDescriptor = catalogDescriptor,
      catalogThreads = catalogData.catalogThreads
    )

    catalogScreenState.postsAsyncDataState.value = AsyncData.Data(catalogThreadsState)

    parseRemainingPostsAsync(
      chanDescriptor = catalogDescriptor,
      postDataList = catalogData.catalogThreads,
      onStartParsingPosts = {
        pushCatalogOrThreadPostsLoadingSnackbar(
          postsCount = catalogData.catalogThreads.size
        )
      },
      onPostsParsed = { postDataList ->
        chanThreadCache.insertCatalogThreads(catalogDescriptor, postDataList)
        popCatalogOrThreadPostsLoadingSnackbar()

        postListBuilt?.await()
        restoreScrollPosition(catalogDescriptor)
        _postsFullyParsedOnceFlow.emit(true)
      }
    )

    logcat {
      "loadCatalog($catalogDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
        "catalogThreads=${catalogData.catalogThreads.size}"
    }
  }

  class CatalogDisplayException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "CatalogScreenViewModel"

    private const val PREV_CATALOG_DESCRIPTOR = "prev_catalog_descriptor"
  }

}