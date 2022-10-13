package com.github.k1rakishou.kurobaexlite.features.boards

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.interactors.catalog.RetrieveSiteCatalogList
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.asLog
import java.util.concurrent.atomic.AtomicBoolean

class CatalogSelectionScreenViewModel(
  private val retrieveSiteCatalogList: RetrieveSiteCatalogList
) : BaseViewModel() {
  private val boardsCache = mutableMapOf<CatalogDescriptor, ChanCatalog>()
  private val loadedBoardsPerSite = mutableMapOf<SiteKey, MutableList<CatalogDescriptor>>()

  private val _loadedBoardsForSite = mutableStateOf<AsyncData<List<ChanBoardUiData>>>(AsyncData.Uninitialized)
  val loadedBoardsForSite: State<AsyncData<List<ChanBoardUiData>>>
    get() = _loadedBoardsForSite

  private val _searchQueryState = mutableStateOf<String?>(null)
  val searchQueryState: State<String?>
    get() = _searchQueryState

  private var boardsForSiteLoading = AtomicBoolean(false)

  fun updateSearchQuery(value: String?) {
    _searchQueryState.value = value
  }

  fun getOrLoadBoardsForSite(
    siteKey: SiteKey,
    forceReload: Boolean,
    hidePullToRefreshIndicator: (() -> Unit)? = null
  ) {
    if (!boardsForSiteLoading.compareAndSet(false, true)) {
      return
    }

    viewModelScope.launch {
      coroutineContext[Job.Key]!!.invokeOnCompletion { boardsForSiteLoading.set(false) }

      val loadedBoardsForSite = loadedBoardsPerSite.getOrPut(
        key = siteKey,
        defaultValue = { mutableListWithCap(64) }
      )

      if (!forceReload && loadedBoardsForSite.isNotEmpty()) {
        val chanBoards = loadedBoardsForSite
          .mapNotNull { catalogDescriptor -> boardsCache[catalogDescriptor] }

        _loadedBoardsForSite.value = AsyncData.Data(chanBoards.map { mapChanBoardToChanBoardUiData(it) })
        return@launch
      }

      val emitLoadingStateJob = coroutineScope {
        launch {
          delay(125L)

          hidePullToRefreshIndicator?.invoke()
          _loadedBoardsForSite.value = AsyncData.Loading
        }
      }

      val siteBoardsResult = try {
        retrieveSiteCatalogList.await(siteKey = siteKey, forceReload = forceReload)
      } finally {
        emitLoadingStateJob.cancel()
      }

      val siteBoards = if (siteBoardsResult.isFailure) {
        val exception = BoardsScreenException(
          "Failed to load site \'$siteKey\' boards, " +
            "error=${siteBoardsResult.exceptionOrThrow().asLog()}"
        )

        _loadedBoardsForSite.value = AsyncData.Error(exception)
        return@launch
      } else {
        siteBoardsResult.getOrThrow()
      }

      if (siteBoards.isEmpty()) {
        val exception = BoardsScreenException("Boards list for site \'$siteKey\' is empty")
        _loadedBoardsForSite.value = AsyncData.Error(exception)
        return@launch
      }

      siteBoards.forEach { chanBoard ->
        boardsCache[chanBoard.catalogDescriptor] = chanBoard

        if (!loadedBoardsForSite.contains(chanBoard.catalogDescriptor)) {
          loadedBoardsForSite.add(chanBoard.catalogDescriptor)
        }
      }

      _loadedBoardsForSite.value = AsyncData.Data(siteBoards.map { mapChanBoardToChanBoardUiData(it) })
    }
  }

  private fun mapChanBoardToChanBoardUiData(chanCatalog: ChanCatalog): ChanBoardUiData {
    val title = buildString {
      append("/")
      append(chanCatalog.catalogDescriptor.boardCode)
      append("/")

      if (chanCatalog.boardTitle.isNotNullNorEmpty()) {
        append(" — ")
        append(chanCatalog.boardTitle)
      }
    }

    return ChanBoardUiData(
      catalogDescriptor = chanCatalog.catalogDescriptor,
      title = title,
      subtitle = chanCatalog.boardDescription
    )
  }

  class BoardsScreenException : ClientException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

}