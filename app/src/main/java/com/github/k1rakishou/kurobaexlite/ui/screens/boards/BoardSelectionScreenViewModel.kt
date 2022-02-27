package com.github.k1rakishou.kurobaexlite.ui.screens.boards

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanBoard
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BoardSelectionScreenViewModel(
  application: KurobaExLiteApplication,
  private val siteManager: SiteManager
) : BaseAndroidViewModel(application) {
  private val boardsCache = mutableMapOf<CatalogDescriptor, ChanBoard>()
  private val loadedBoardsPerSite = mutableMapOf<SiteKey, MutableList<CatalogDescriptor>>()

  fun getOrLoadBoardsForSite(siteKey: SiteKey): Flow<AsyncData<List<ChanBoardUiData>>> {
    return flow {
      val loadedBoardsForSite = loadedBoardsPerSite.getOrPut(siteKey, defaultValue = { mutableListWithCap(64) })
      if (loadedBoardsForSite.isNotEmpty()) {
        val chanBoards = loadedBoardsForSite.mapNotNull { catalogDescriptor -> boardsCache[catalogDescriptor] }
        emit(AsyncData.Data(chanBoards.map { mapChanBoardToChanBoardUiData(it) }))
        return@flow
      }

      emit(AsyncData.Loading)

      val site = siteManager.bySiteKey(siteKey)
      if (site == null) {
        val exception = BoardsScreenException("No site found by key \'$siteKey\'")
        emit(AsyncData.Error(exception))
        return@flow
      }

      val loadBoardsResult = site.boardsInfo()?.siteBoardsDataSource()?.loadBoards(siteKey)
      if (loadBoardsResult == null) {
        val exception = BoardsScreenException("Site with key \'$siteKey\' does not support board list")
        emit(AsyncData.Error(exception))
        return@flow
      }

      val loadBoardsError = loadBoardsResult.exceptionOrNull()
      if (loadBoardsError != null) {
        val exception = BoardsScreenException("Failed to load board list for site \'$siteKey\'", loadBoardsError)
        emit(AsyncData.Error(exception))
        return@flow
      }

      val chanBoards = loadBoardsResult.getOrThrow().chanBoards
        .sortedBy { it.catalogDescriptor.boardCode }

      if (chanBoards.isEmpty()) {
        val exception = BoardsScreenException("Boards list for site \'$siteKey\' is empty")
        emit(AsyncData.Error(exception))
        return@flow
      }

      chanBoards.forEach { chanBoard ->
        boardsCache[chanBoard.catalogDescriptor] = chanBoard
        loadedBoardsForSite.add(chanBoard.catalogDescriptor)
      }

      emit(AsyncData.Data(chanBoards.map { mapChanBoardToChanBoardUiData(it) }))
    }
  }

  private fun mapChanBoardToChanBoardUiData(chanBoard: ChanBoard): ChanBoardUiData {
    val title = buildString {
      append("/")
      append(chanBoard.catalogDescriptor.boardCode)
      append("/")

      if (chanBoard.boardTitle.isNotNullNorEmpty()) {
        append(" — ")
        append(chanBoard.boardTitle)
      }
    }

    return ChanBoardUiData(
      catalogDescriptor = chanBoard.catalogDescriptor,
      title = title,
      subtitle = chanBoard.boardDescription
    )
  }

  class BoardsScreenException : ClientException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

}