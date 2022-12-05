package com.github.k1rakishou.kurobaexlite.interactors.catalog

import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.managers.CatalogManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.entity.CatalogKey
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogEntitySorted
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogFlagEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogSortOrderEntity
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.database.Daos
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import logcat.logcat

class RetrieveSiteCatalogList(
  private val siteManager: SiteManager,
  private val catalogManager: CatalogManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun await(siteKey: SiteKey, forceReload: Boolean): Result<List<ChanCatalog>> {
    return Result.Try {
      logcat(TAG) { "siteKey=$siteKey, forceReload=$forceReload" }

      val site = siteManager.bySiteKey(siteKey)
      if (site == null) {
        throw GetSiteBoardListException("No site found by key \'$siteKey\'")
      }

      if (!forceReload) {
        logcat(TAG) { "Reading from database..." }

        val readCatalogsResult = kurobaExLiteDatabase.transaction {
          val catalogListFromDatabase = chanCatalogDao.selectAllForSiteOrdered(siteKey.key)

          return@transaction catalogListFromDatabase
            .sortedBy { catalogEntity -> catalogEntity.chanCatalogSortOrderEntity.sortOrder }
            .map { catalogEntity ->
              val flags = chanCatalogFlagDao.selectCatalogFlags(
                siteKey = catalogEntity.chanCatalogEntity.catalogKey.siteKey,
                boardCode = catalogEntity.chanCatalogEntity.catalogKey.boardCode
              ).map { chanCatalogFlagEntity -> BoardFlag(chanCatalogFlagEntity.flagKey, chanCatalogFlagEntity.flagName) }

              return@map catalogEntity.toChanCatalog(flags)
            }
        }

        if (readCatalogsResult.isSuccess) {
          logcat(TAG) { "Reading from database... SUCCESS" }

          val chanCatalogsFromDatabase = readCatalogsResult.getOrThrow()
          if (chanCatalogsFromDatabase.isNotEmpty()) {
            logcat(TAG) { "Got catalogs from the database: count=${chanCatalogsFromDatabase.size}" }

            catalogManager.insertMany(chanCatalogsFromDatabase)
            return@Try chanCatalogsFromDatabase
          }

          logcat(TAG) { "Cached catalogs cannot be used: cachedCatalogsCount: ${chanCatalogsFromDatabase.size}" }

          // fallthrough
        } else {
          logcat(TAG) {
            "Reading from database...ERROR, " +
              "error=${readCatalogsResult.exceptionOrThrow().errorMessageOrClassName()}"
          }

          // fallthrough
        }
      }

      val loadBoardsResult = site.boardsInfo()?.siteBoardsDataSource()?.loadBoards(siteKey)
      if (loadBoardsResult == null) {
        logcatError(TAG) { "siteBoardsDataSource.loadBoards($siteKey) -> null" }
        throw GetSiteBoardListException("Site with key \'$siteKey\' does not support board list")
      }

      val loadBoardsError = loadBoardsResult.exceptionOrNull()
      if (loadBoardsError != null) {
        logcatError(TAG) { "siteBoardsDataSource.loadBoards($siteKey) -> error (${loadBoardsError.asLogIfImportantOrErrorMessage()})" }
        throw GetSiteBoardListException("Failed to load board list for site \'$siteKey\'", loadBoardsError)
      }

      val siteCatalogs = loadBoardsResult.getOrThrow().chanCatalogs
        .sortedBy { it.catalogDescriptor.boardCode }

      if (siteCatalogs.isEmpty()) {
        logcatError(TAG) { "siteBoardsDataSource.loadBoards($siteKey) -> EmptyList" }
        return@Try emptyList()
      }

      logcat(TAG) { "Got boards from network: count=${siteCatalogs.size}" }

      kurobaExLiteDatabase.transaction {
        logcat(TAG) { "Inserting ${siteCatalogs.size} siteCatalogs into database..." }

        val catalogEntityListSorted = chanCatalogDao.selectAllForSiteOrdered(siteKey = siteKey.key)
        val databaseIdMap = mutableMapWithCap<ChanDescriptor, Long>(siteCatalogs.size)

        logcat(TAG) { "Loaded ${catalogEntityListSorted.size} existing catalog entities from the database" }

        catalogEntityListSorted.forEach { chanCatalogEntitySorted ->
          val databaseId = chanCatalogEntitySorted.chanCatalogEntity.databaseId
          val catalogDescriptor = chanCatalogEntitySorted.chanCatalogEntity.catalogKey.catalogDescriptor

          check(databaseId > 0L) { "Bad databaseId: ${databaseId}" }
          databaseIdMap[catalogDescriptor] = databaseId
        }

        insertOrUpdateBoards(siteCatalogs, databaseIdMap)
        insertOrUpdateBoardOrders(catalogEntityListSorted, siteCatalogs, databaseIdMap)
        insertOrUpdateBoardFlags(siteCatalogs)
      }.onFailure { error ->
        logcatError(TAG) { "Inserting ${siteCatalogs.size} siteCatalogs into database... ERROR, error=${error.errorMessageOrClassName()}" }
      }.onSuccess {
        logcat(TAG) { "Inserting ${siteCatalogs.size} siteCatalogs into database... SUCCESS" }
      }

      catalogManager.insertMany(siteCatalogs)

      return@Try siteCatalogs
    }
  }

  private suspend fun Daos.insertOrUpdateBoardFlags(siteCatalogs: List<ChanCatalog>) {
    siteCatalogs.forEach { siteCatalog ->
      if (siteCatalog.flags.isEmpty()) {
        return@forEach
      }

      val siteKey = siteCatalog.catalogDescriptor.siteKeyActual
      val boardCode = siteCatalog.catalogDescriptor.boardCode

      val chanCatalogFlagEntities = siteCatalog.flags.mapIndexed { sortOrder, boardFlag ->
        ChanCatalogFlagEntity(
          catalogKey = CatalogKey(
            siteKey = siteKey,
            boardCode = boardCode
          ),
          flagKey = boardFlag.key,
          flagName = boardFlag.name,
          sortOrder = sortOrder
        )
      }

      chanCatalogFlagDao.replaceCatalogFlags(
        siteKey = siteKey,
        boardCode = boardCode,
        newChanCatalogFlagEntities = chanCatalogFlagEntities
      )
    }
  }

  private suspend fun Daos.insertOrUpdateBoardOrders(
    catalogEntityListSorted: List<ChanCatalogEntitySorted>,
    siteCatalogs: List<ChanCatalog>,
    databaseIdMap: Map<ChanDescriptor, Long>
  ) {
    run {
      val chanCatalogSortOrderEntityList = mutableListWithCap<ChanCatalogSortOrderEntity>(
        initialCapacity = catalogEntityListSorted.size + siteCatalogs.size
      )
      chanCatalogSortOrderEntityList.addAll(
        catalogEntityListSorted.map { it.chanCatalogSortOrderEntity }
      )

      val maxSortOrder = chanCatalogSortOrderEntityList.lastOrNull()?.sortOrder?.plus(1) ?: 0

      siteCatalogs.forEachIndexed { index, chanCatalog ->
        val databaseId = databaseIdMap[chanCatalog.catalogDescriptor]
        if (databaseId == null || databaseId <= 0L) {
          return@forEachIndexed
        }

        val alreadyExists = chanCatalogSortOrderEntityList
          .indexOfFirst { it.ownerDatabaseId == databaseId } >= 0

        if (alreadyExists) {
          return@forEachIndexed
        }

        chanCatalogSortOrderEntityList += ChanCatalogSortOrderEntity(
          ownerDatabaseId = databaseId,
          sortOrder = maxSortOrder + index
        )
      }

      logcat(TAG) { "replaceChanCatalogSortOrderEntityList() chanCatalogSortOrderEntityList=${chanCatalogSortOrderEntityList.size}" }
      if (chanCatalogSortOrderEntityList.isNotEmpty()) {
        chanCatalogDao.replaceChanCatalogSortOrderEntityList(chanCatalogSortOrderEntityList)
      }
    }
  }

  private suspend fun Daos.insertOrUpdateBoards(
    siteCatalogs: List<ChanCatalog>,
    databaseIdMap: MutableMap<ChanDescriptor, Long>
  ) {
    kotlin.run {
      val toInsert = mutableListWithCap<ChanCatalogEntity>(siteCatalogs.size / 2)
      val toUpdate = mutableListWithCap<ChanCatalogEntity>(siteCatalogs.size / 2)

      siteCatalogs.forEach { chanCatalog ->
        if (databaseIdMap.containsKey(chanCatalog.catalogDescriptor)) {
          toUpdate += ChanCatalogEntity(
            catalogKey = CatalogKey.fromCatalogDescriptor(chanCatalog.catalogDescriptor),
            databaseId = databaseIdMap[chanCatalog.catalogDescriptor]!!,
            boardTitle = chanCatalog.boardTitle,
            boardDescription = chanCatalog.boardDescription,
            workSafe = chanCatalog.workSafe,
            bumpLimit = chanCatalog.bumpLimit,
          )
        } else {
          toInsert += ChanCatalogEntity(
            catalogKey = CatalogKey.fromCatalogDescriptor(chanCatalog.catalogDescriptor),
            boardTitle = chanCatalog.boardTitle,
            boardDescription = chanCatalog.boardDescription,
            workSafe = chanCatalog.workSafe,
            bumpLimit = chanCatalog.bumpLimit,
          )
        }
      }

      logcat(TAG) { "insertChanCatalogEntityList() toInsert=${toInsert.size}" }
      if (toInsert.isNotEmpty()) {
        val insertDatabaseIdMap = chanCatalogDao.insertChanCatalogEntityList(toInsert)
        databaseIdMap.putAll(insertDatabaseIdMap)
      }

      logcat(TAG) { "updateChanCatalogEntityList() toUpdate=${toUpdate.size}" }
      if (toUpdate.isNotEmpty()) {
        chanCatalogDao.updateChanCatalogEntityList(toUpdate)
      }
    }
  }

  class GetSiteBoardListException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  companion object {
    private const val TAG = "GetSiteBoardList"
  }

}