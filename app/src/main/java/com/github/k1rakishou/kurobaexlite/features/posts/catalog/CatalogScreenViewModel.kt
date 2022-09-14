package com.github.k1rakishou.kurobaexlite.features.posts.catalog

import android.os.SystemClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.CatalogScreenPostsState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.sort.CatalogThreadSorter
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.interactors.navigation.LoadNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.UpdateChanCatalogView
import com.github.k1rakishou.kurobaexlite.managers.LastVisitedEndpointManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.PostsLoadResult
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat

class CatalogScreenViewModel(
  savedStateHandle: SavedStateHandle,
  private val updateChanCatalogView: UpdateChanCatalogView,
  private val lastVisitedEndpointManager: LastVisitedEndpointManager,
  private val loadNavigationHistory: LoadNavigationHistory,
) : PostScreenViewModel(savedStateHandle) {
  private val screenKey: ScreenKey = CatalogScreen.SCREEN_KEY

  private val catalogScreenState = CatalogScreenPostsState()
  private val updateChanCatalogViewExecutor = DebouncingCoroutineExecutor(viewModelScope)

  private var loadCatalogJob: Job? = null

  val catalogDescriptor: CatalogDescriptor?
    get() = chanDescriptor as? CatalogDescriptor

  override val postScreenState: PostScreenState = catalogScreenState

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    loadPrevVisitedCatalog()
  }

  private suspend fun loadPrevVisitedCatalog() {
    val lastVisitedCatalogDescriptor = savedStateHandle.get<CatalogDescriptor>(LAST_VISITED_CATALOG_KEY)
    if (lastVisitedCatalogDescriptor != null) {
      logcat(tag = TAG) { "loadPrevVisitedCatalog() got ${lastVisitedCatalogDescriptor} from savedStateHandle" }

      loadCatalog(
        catalogDescriptor = lastVisitedCatalogDescriptor,
        loadOptions = LoadOptions(forced = true)
      )

      return
    }

    val lastVisitedCatalog = loadNavigationHistory.lastVisitedCatalog()
    if (lastVisitedCatalog != null) {
      logcat(tag = TAG) { "loadPrevVisitedCatalog() got ${lastVisitedCatalog} from loadNavigationHistory" }

      lastVisitedEndpointManager.notifyRestoreLastVisitedCatalog(lastVisitedCatalog)
      return
    }

    logcat(tag = TAG) { "loadPrevVisitedCatalog() prevCatalogDescriptor is null" }
  }

  override fun reload(
    loadOptions: LoadOptions,
    onReloadFinished: (() -> Unit)?
  ) {
    val currentlyOpenedCatalog = chanThreadManager.currentlyOpenedCatalog

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

    if (catalogDescriptor != null && loadOptions.showLoadingIndicator) {
      resetPosition(catalogDescriptor)
    }

    snackbarManager.popSnackbar(SnackbarId.ReloadLastVisitedCatalog)

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
    val alreadyShowingPosts = catalogScreenState.postsAsyncDataState.value is AsyncData.Data<PostsState>
    val reloadingTheSameCatalog = chanThreadManager.currentlyOpenedCatalog == catalogDescriptor

    if (alreadyShowingPosts && reloadingTheSameCatalog && !loadOptions.forced) {
      onReloadFinished?.invoke()
      return
    }

    val startTime = SystemClock.elapsedRealtime()
    postScreenState.lastLoadErrorState.value = null
    onCatalogLoadingStart(catalogDescriptor, loadOptions)
    globalUiInfoManager.onLoadingErrorUpdatedOrRemoved(screenKey, false)

    val postsLoadResultMaybe = if (loadOptions.loadFromNetwork || catalogDescriptor == null) {
      if (loadOptions.deleteCached && catalogDescriptor != null) {
        chanThreadManager.delete(catalogDescriptor)
      }

      chanThreadManager.loadCatalog(catalogDescriptor)
    } else {
      val catalogThreads = chanCache.getCatalogThreads(catalogDescriptor)
      val postsLoadResult = PostsLoadResult(
        chanDescriptor = catalogDescriptor,
        updatedPosts = catalogThreads,
        newPosts = emptyList()
      )

      Result.success(postsLoadResult)
    }

    if (postsLoadResultMaybe.isFailure) {
      val error = postsLoadResultMaybe.exceptionOrThrow()
      logcatError { "loadCatalogInternal() error=${error.asLog()}" }

      catalogScreenState.lastLoadErrorState.value = error
      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      globalUiInfoManager.onLoadingErrorUpdatedOrRemoved(screenKey, true)

      onCatalogLoadingEnd(catalogDescriptor)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val postsLoadResult = postsLoadResultMaybe.unwrap()
    if (postsLoadResult == null || catalogDescriptor == null) {
      catalogScreenState.postsAsyncDataState.value = AsyncData.Uninitialized
      onCatalogLoadingEnd(catalogDescriptor)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    if (postsLoadResult.isEmpty()) {
      val error = CatalogDisplayException("Catalog /${catalogDescriptor}/ has no posts")

      catalogScreenState.postsAsyncDataState.value = AsyncData.Error(error)
      onCatalogLoadingEnd(catalogDescriptor)
      _postsFullyParsedOnceFlow.emit(true)
      onReloadFinished?.invoke()

      return
    }

    val catalogSortSetting = appSettings.catalogSort.read()
    val catalogPostViewMode = appSettings.catalogPostViewMode.read().toPostViewMode()

    val sortedThreads = CatalogThreadSorter.sortCatalogPostData(
      catalogThreads = postsLoadResult.allCombinedForCatalog(),
      catalogSortSetting = catalogSortSetting
    )

    val initialParsedPosts = parseInitialBatchOfPosts(
      startPostDescriptor = null,
      chanDescriptor = catalogDescriptor,
      postDataList = sortedThreads,
      isCatalogMode = true,
      postViewMode = catalogPostViewMode,
      forced = true
    )

    val catalogThreadsState = PostsState(catalogDescriptor)

    Snapshot.withMutableSnapshot {
      catalogScreenState.postsAsyncDataState.value = AsyncData.Data(catalogThreadsState)
      catalogScreenState.insertOrUpdateMany(initialParsedPosts)
    }

    parseRemainingPostsAsync(
      chanDescriptor = catalogDescriptor,
      postDataList = sortedThreads,
      postViewMode = catalogPostViewMode,
      parsePostsOptions = ParsePostsOptions(forced = true),
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
          "loadCatalogInternal($catalogDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
            "catalogThreads=${sortedThreads.size}"
        }

        try {
          catalogScreenState.insertOrUpdateMany(postCellDataList)
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

  override fun onPostScrollChanged(
    firstVisiblePostData: PostCellData,
    lastVisiblePostData: PostCellData,
    postListTouchingBottom: Boolean
  ) {
    updateChanCatalogViewExecutor.post(timeout = 200L) {
      val catalogDescriptor = chanDescriptor as? CatalogDescriptor
        ?: return@post

      updateChanCatalogView.execute(
        catalogDescriptor = catalogDescriptor,
        catalogBoundPostDescriptor = firstVisiblePostData.postDescriptor
      )
    }
  }

  class CatalogDisplayException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "CatalogScreenViewModel"
  }

}