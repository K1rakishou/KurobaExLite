package com.github.k1rakishou.kurobaexlite.interactors.catalog

import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.managers.CatalogManager
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor

class LoadChanCatalog(
  private val catalogManager: CatalogManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun await(chanDescriptor: ChanDescriptor): Result<ChanCatalog?> {
    return Result.Try {
      val catalogDescriptor = chanDescriptor.catalogDescriptor()

      val catalogFromCache = catalogManager.byCatalogDescriptor(catalogDescriptor)
      if (catalogFromCache != null) {
        return@Try catalogFromCache
      }

      val catalogFromDatabase = kurobaExLiteDatabase.transaction {
        val catalogEntity = chanCatalogDao.selectCatalog(
          siteKey = catalogDescriptor.siteKeyActual,
          boardCode = catalogDescriptor.boardCode
        )

        if (catalogEntity == null) {
          return@transaction null
        }

        val flags = chanCatalogFlagDao.selectCatalogFlags(
          siteKey = catalogEntity.catalogKey.siteKey,
          boardCode = catalogEntity.catalogKey.boardCode
        ).map { chanCatalogFlagEntity ->
          return@map BoardFlag(
            key = chanCatalogFlagEntity.flagKey,
            name = chanCatalogFlagEntity.flagName,
            flagId = chanCatalogFlagEntity.flagId
          )
        }

        return@transaction catalogEntity.toChanCatalog(flags)
      }.unwrap()

      if (catalogFromDatabase != null) {
        catalogManager.insert(catalogFromDatabase)
      }

      return@Try catalogFromDatabase
    }
  }

}