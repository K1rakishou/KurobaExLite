package com.github.k1rakishou.kurobaexlite.features.boards

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.base.asAsyncData
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadCatalogsForAllSites
import com.github.k1rakishou.kurobaexlite.interactors.catalog.RetrieveSiteCatalogList
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.asLog
import java.util.concurrent.atomic.AtomicBoolean

class CatalogSelectionScreenViewModel(
  private val retrieveSiteCatalogList: RetrieveSiteCatalogList,
  private val loadCatalogsForAllSites: LoadCatalogsForAllSites
) : BaseViewModel() {
  private val boardsCache = mutableMapOf<CatalogDescriptor, ChanCatalog>()
  private val loadedBoardsPerSite = mutableMapOf<SiteKey, MutableList<CatalogDescriptor>>()

  private val _loadedCatalogsForAllSites = mutableStateOf<AsyncData<List<ChanCatalogUiData>>>(AsyncData.Uninitialized)
  val loadedCatalogsForAllSites: State<AsyncData<List<ChanCatalogUiData>>>
    get() = _loadedCatalogsForAllSites

  private val _loadedCatalogsForCurrentSite = mutableStateOf<AsyncData<List<ChanCatalogUiData>>>(AsyncData.Uninitialized)
  val loadedCatalogsForCurrentSite: State<AsyncData<List<ChanCatalogUiData>>>
    get() = _loadedCatalogsForCurrentSite

  private val _searchQueryState = mutableStateOf<String?>(null)
  val searchQueryState: State<String?>
    get() = _searchQueryState

  private val boardsForSiteLoading = AtomicBoolean(false)
  private var reloadAllSiteCatalogsJob: Job? = null

  fun updateSearchQuery(query: String?) {
    val wasQueryNull = _searchQueryState.value == null

    if (wasQueryNull && query != null) {
      reloadAllSiteCatalogsJob?.cancel()
      reloadAllSiteCatalogsJob = viewModelScope.launch { loadAllSiteCatalogs() }
    } else if (!wasQueryNull && query == null) {
      reloadAllSiteCatalogsJob?.cancel()
    }

    _searchQueryState.value = query
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
      loadCatalogsForSite(
        coroutineScope = this,
        siteKey = siteKey,
        forceReload = forceReload,
        hidePullToRefreshIndicator = hidePullToRefreshIndicator
      )
    }
  }

  private suspend fun loadCatalogsForSite(
    coroutineScope: CoroutineScope,
    siteKey: SiteKey,
    forceReload: Boolean,
    hidePullToRefreshIndicator: (() -> Unit)?
  ) {
    if (true) {
      hidePullToRefreshIndicator?.invoke()
      _loadedCatalogsForCurrentSite.value = (0..100).map { index ->
        ChanCatalogUiData(
          siteKey = Chan4.SITE_KEY,
          catalogDescriptor = CatalogDescriptor(Chan4.SITE_KEY, "${index}"),
          title = "Test catalog ${index}",
          subtitle = "${index}"
        )
      }.asAsyncData()

      return
    }

    coroutineScope.coroutineContext[Job]!!.invokeOnCompletion { boardsForSiteLoading.set(false) }

    val loadedBoardsForSite = loadedBoardsPerSite.getOrPut(
      key = siteKey,
      defaultValue = { mutableListWithCap(64) }
    )

    if (!forceReload && loadedBoardsForSite.isNotEmpty()) {
      val chanBoards = loadedBoardsForSite
        .mapNotNull { catalogDescriptor -> boardsCache[catalogDescriptor] }

      _loadedCatalogsForCurrentSite.value = chanBoards.map { mapChanBoardToChanBoardUiData(siteKey, it) }
        .asAsyncData()

      return
    }

    val emitLoadingStateJob = coroutineScope {
      launch {
        delay(125L)

        hidePullToRefreshIndicator?.invoke()
        _loadedCatalogsForCurrentSite.value = AsyncData.Loading
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

      _loadedCatalogsForCurrentSite.value = AsyncData.Error(exception)
      return
    } else {
      siteBoardsResult.getOrThrow()
    }

    if (siteBoards.isEmpty()) {
      val exception = BoardsScreenException("Boards list for site \'$siteKey\' is empty")
      _loadedCatalogsForCurrentSite.value = AsyncData.Error(exception)
      return
    }

    siteBoards.forEach { chanBoard ->
      boardsCache[chanBoard.catalogDescriptor] = chanBoard

      if (!loadedBoardsForSite.contains(chanBoard.catalogDescriptor)) {
        loadedBoardsForSite.add(chanBoard.catalogDescriptor)
      }
    }

    _loadedCatalogsForCurrentSite.value = siteBoards.map { mapChanBoardToChanBoardUiData(siteKey, it) }
      .asAsyncData()
  }

  private suspend fun loadAllSiteCatalogs() {
    val allSites = mutableListWithCap<ChanCatalogUiData>(32)
    val loadedCatalogs = loadCatalogsForAllSites.await()

    loadedCatalogs
      .entries
      .forEach { (siteKey, chanCatalogs) ->
        val catalogs = chanCatalogs.map { chanCatalog -> mapChanBoardToChanBoardUiData(siteKey, chanCatalog) }

        allSites += catalogs
        loadedBoardsPerSite[siteKey] = catalogs.map { it.catalogDescriptor }.toMutableList()
        chanCatalogs.forEach { chanCatalog -> boardsCache[chanCatalog.catalogDescriptor] = chanCatalog }
      }

    _loadedCatalogsForAllSites.value = allSites.asAsyncData()
  }

  private fun mapChanBoardToChanBoardUiData(siteKey: SiteKey, chanCatalog: ChanCatalog): ChanCatalogUiData {
    val title = buildString {
      append("/")
      append(chanCatalog.catalogDescriptor.boardCode)
      append("/")

      if (chanCatalog.boardTitle.isNotNullNorEmpty()) {
        append(" — ")
        append(chanCatalog.boardTitle)
      }
    }

    return ChanCatalogUiData(
      siteKey = siteKey,
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