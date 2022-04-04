package com.github.k1rakishou.kurobaexlite.ui.screens.boards

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.interactors.GetSiteBoardList
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.SiteBoard
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import logcat.asLog
import org.koin.java.KoinJavaComponent.inject

class BoardSelectionScreenViewModel(
  application: KurobaExLiteApplication
) : BaseAndroidViewModel(application) {
  private val getSiteBoardList: GetSiteBoardList by inject(GetSiteBoardList::class.java)

  private val boardsCache = mutableMapOf<CatalogDescriptor, SiteBoard>()
  private val loadedBoardsPerSite = mutableMapOf<SiteKey, MutableList<CatalogDescriptor>>()

  fun getOrLoadBoardsForSite(
    siteKey: SiteKey,
    page: Int,
    forceReload: Boolean
  ): Flow<AsyncData<List<ChanBoardUiData>>> {
    return channelFlow {
      val loadedBoardsForSite = loadedBoardsPerSite.getOrPut(
        key = siteKey,
        defaultValue = { mutableListWithCap(64) }
      )

      if (!forceReload) {
        if (loadedBoardsForSite.isNotEmpty()) {
          val chanBoards = loadedBoardsForSite
            .mapNotNull { catalogDescriptor -> boardsCache[catalogDescriptor] }

          send(AsyncData.Data(chanBoards.map { mapChanBoardToChanBoardUiData(it) }))
          return@channelFlow
        }
      }

      val emitLoadingStateJob = coroutineScope {
        launch {
          if (forceReload) {
            return@launch
          }

          delay(125L)
          send(AsyncData.Loading)
        }
      }

      val siteBoardsResult = try {
        getSiteBoardList.await(siteKey, page, forceReload)
      } finally {
        emitLoadingStateJob.cancel()
      }

      val siteBoards = if (siteBoardsResult.isFailure) {
        val exception = BoardsScreenException(
          "Failed to load site \'$siteKey\' boards, " +
            "error=${siteBoardsResult.exceptionOrThrow().asLog()}"
        )

        send(AsyncData.Error(exception))
        return@channelFlow
      } else {
        siteBoardsResult.getOrThrow()
      }

      if (siteBoards.isEmpty()) {
        val exception = BoardsScreenException("Boards list for site \'$siteKey\' is empty")
        send(AsyncData.Error(exception))
        return@channelFlow
      }

      siteBoards.forEach { chanBoard ->
        boardsCache[chanBoard.catalogDescriptor] = chanBoard
        loadedBoardsForSite.add(chanBoard.catalogDescriptor)
      }

      send(AsyncData.Data(siteBoards.map { mapChanBoardToChanBoardUiData(it) }))
    }
  }

  private fun mapChanBoardToChanBoardUiData(siteBoard: SiteBoard): ChanBoardUiData {
    val title = buildString {
      append("/")
      append(siteBoard.catalogDescriptor.boardCode)
      append("/")

      if (siteBoard.boardTitle.isNotNullNorEmpty()) {
        append(" — ")
        append(siteBoard.boardTitle)
      }
    }

    return ChanBoardUiData(
      catalogDescriptor = siteBoard.catalogDescriptor,
      title = title,
      subtitle = siteBoard.boardDescription
    )
  }

  class BoardsScreenException : ClientException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

}