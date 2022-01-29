package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat

class CatalogScreenViewModel(
  private val chanThreadManager: ChanThreadManager,
  globalConstants: GlobalConstants,
  postCommentParser: PostCommentParser,
  postCommentApplier: PostCommentApplier,
  themeEngine: ThemeEngine
) : PostScreenViewModel(globalConstants, postCommentParser, postCommentApplier, themeEngine) {
  private val catalogScreenState = CatalogScreenState()
  private var loadCatalogJob: Job? = null

  override val postScreenState: PostScreenState = catalogScreenState

  override fun reload() {
    val currentlyOpenedCatalog = chanThreadManager.currentlyOpenedCatalog

    loadCatalog(
      catalogDescriptor = currentlyOpenedCatalog,
      forced = true
    )
  }

  fun loadCatalog(
    catalogDescriptor: CatalogDescriptor?,
    forced: Boolean = false
  ) {
    loadCatalogJob?.cancel()
    loadCatalogJob = null

    loadCatalogJob = viewModelScope.launch { loadCatalogInternal(catalogDescriptor, forced) }
  }

  private suspend fun loadCatalogInternal(
    catalogDescriptor: CatalogDescriptor?,
    forced: Boolean = false
  ) {
    if (!forced && chanThreadManager.currentlyOpenedCatalog == catalogDescriptor) {
      return
    }

    val startTime = SystemClock.elapsedRealtime()
    catalogScreenState.catalogThreadsAsync = AsyncData.Loading

    val catalogDataResult = chanThreadManager.loadCatalog(catalogDescriptor)
    if (catalogDataResult.isFailure) {
      val error = catalogDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      catalogScreenState.catalogThreadsAsync = AsyncData.Error(error)
      return
    }

    val catalogData = catalogDataResult.unwrap()
    if (catalogData == null || catalogDescriptor == null) {
      catalogScreenState.catalogThreadsAsync = AsyncData.Empty
      return
    }

    if (catalogData.catalogThreads.isEmpty()) {
      val error = CatalogDisplayException("Catalog /${catalogDescriptor}/ has no posts")
      catalogScreenState.catalogThreadsAsync = AsyncData.Error(error)
      return
    }

    parsePostsAround(
      startIndex = 0,
      postDataList = catalogData.catalogThreads,
      count = 16
    )

    parseRemainingPostsAsync(catalogData.catalogThreads)

    val catalogThreadsState = CatalogThreadsState(
      catalogDescriptor = catalogDescriptor,
      catalogThreads = catalogData.catalogThreads
    )

    catalogScreenState.catalogThreadsAsync = AsyncData.Data(catalogThreadsState)

    logcat {
      "loadCatalog($catalogDescriptor) took ${SystemClock.elapsedRealtime() - startTime} ms, " +
        "catalogThreads=${catalogData.catalogThreads.size}"
    }
  }

  class CatalogDisplayException(message: String) : ClientException(message)

  class CatalogScreenState(
    private val catalogThreadsAsyncState: MutableState<AsyncData<CatalogThreadsState>> = mutableStateOf(AsyncData.Empty)
  ) : PostScreenState {
    internal var catalogThreadsAsync by catalogThreadsAsyncState

    override fun postDataAsync(): AsyncData<List<PostData>> {
      return when (val asyncDataStateValue = catalogThreadsAsyncState.value) {
        is AsyncData.Data -> AsyncData.Data(asyncDataStateValue.data.catalogThreads)
        is AsyncData.Error -> AsyncData.Error(asyncDataStateValue.error)
        AsyncData.Empty -> AsyncData.Empty
        AsyncData.Loading -> AsyncData.Loading
      }
    }
  }

  class CatalogThreadsState(
    val catalogDescriptor: CatalogDescriptor,
    catalogThreads: List<PostData>
  ) {
    private val _catalogThreads = mutableStateListOf<PostData>()
    val catalogThreads: List<PostData>
      get() = _catalogThreads

    init {
      _catalogThreads.addAll(catalogThreads)
    }
  }

}