package com.github.k1rakishou.kurobaexlite.interactors

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.cache.site_data.PersistableSiteDataKey
import com.github.k1rakishou.kurobaexlite.helpers.cache.site_data.SiteDataPersister
import com.github.k1rakishou.kurobaexlite.helpers.cache.site_data.segments
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.PersistableSiteBoard
import com.github.k1rakishou.kurobaexlite.model.data.local.PersistableSiteBoards
import com.github.k1rakishou.kurobaexlite.model.data.local.SiteBoard
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import java.util.concurrent.TimeUnit
import logcat.logcat

class GetSiteBoardList(
  private val siteManager: SiteManager,
  private val siteDataPersister: SiteDataPersister
) {

  suspend fun await(siteKey: SiteKey, page: Int, forceReload: Boolean): Result<List<SiteBoard>> {
    return Result.Try {
      logcat(TAG) { "siteKey=$siteKey, forceReload=$forceReload" }

      val site = siteManager.bySiteKey(siteKey)
      if (site == null) {
        throw GetSiteBoardListException("No site found by key \'$siteKey\'")
      }

      val key = PersistableSiteDataKey.create(
        segments = segments(siteKey.key),
        fileName = PersistableSiteBoards.boardList(page)
      )

      if (!forceReload) {
        logcat(TAG) { "Reading from disk..." }

        val readBoardsResult = siteDataPersister.read<PersistableSiteBoards>(key)
        if (readBoardsResult.isSuccess) {
          logcat(TAG) { "Reading from disk... SUCCESS" }

          val persistedSiteBoards = readBoardsResult.getOrThrow()
          val cacheIsFresh = (persistedSiteBoards.cachedDate + CACHE_MAX_LIFE_TIME_MS) > System.currentTimeMillis()

          if (persistedSiteBoards.list.isNotEmpty() && cacheIsFresh) {
            logcat(TAG) { "Got boards from disk cache: count=${persistedSiteBoards.list.size}" }

            return@Try persistedSiteBoards.list
              .map { persistedSiteBoard -> persistedSiteBoard.toSiteBoard() }
          }

          logcat(TAG) {
            "Cached boards cannot be used: " +
              "cachedBoardsCount: ${persistedSiteBoards.list.size}, " +
              "cacheIsFresh=$cacheIsFresh"
          }

          // fallthrough
        } else {
          logcat(TAG) {
            "Reading from disk...ERROR, " +
              "error=${readBoardsResult.exceptionOrThrow().errorMessageOrClassName()}"
          }

          // fallthrough
        }
      }

      val loadBoardsResult = site.boardsInfo()?.siteBoardsDataSource()?.loadBoards(siteKey)
      if (loadBoardsResult == null) {
        throw GetSiteBoardListException("Site with key \'$siteKey\' does not support board list")
      }

      val loadBoardsError = loadBoardsResult.exceptionOrNull()
      if (loadBoardsError != null) {
        throw GetSiteBoardListException("Failed to load board list for site \'$siteKey\'", loadBoardsError)
      }

      val siteBoards = loadBoardsResult.getOrThrow().siteBoards
        .sortedBy { it.catalogDescriptor.boardCode }

      logcat(TAG) { "Got boards from network: count=${siteBoards.size}" }

      val persistableSiteBoards = siteBoards.mapIndexed { sortOrder, siteBoard ->
        PersistableSiteBoard.fromSiteBoard(sortOrder, siteBoard)
      }

      if (persistableSiteBoards.isNotEmpty()) {
        logcat(TAG) { "Persisting on disk..." }

        val persistResult = siteDataPersister.write(
          key = key,
          data = PersistableSiteBoards(
            cachedDate = System.currentTimeMillis(),
            page = page,
            list = persistableSiteBoards
          )
        )

        if (persistResult.isFailure) {
          logcat(TAG) {
            "Persisting on disk... ERROR, " +
              "error=${persistResult.exceptionOrThrow().errorMessageOrClassName()}"
          }
        } else {
          logcat(TAG) { "Persisting on disk... SUCCESS" }
        }
      }

      return@Try siteBoards
    }
  }

  class GetSiteBoardListException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  companion object {
    private const val TAG = "GetSiteBoardList"
    private val CACHE_MAX_LIFE_TIME_MS = TimeUnit.DAYS.toMillis(7)
  }

}