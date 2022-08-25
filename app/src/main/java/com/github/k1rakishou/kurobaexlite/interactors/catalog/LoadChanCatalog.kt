package com.github.k1rakishou.kurobaexlite.interactors.catalog

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.managers.CatalogManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

class LoadChanCatalog(
  private val catalogManager: CatalogManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun await(chanDescriptor: ChanDescriptor): Result<ChanCatalog?> {
    return Result.Try {
      val catalogDescriptor = when (chanDescriptor) {
        is CatalogDescriptor -> chanDescriptor
        is ThreadDescriptor -> chanDescriptor.catalogDescriptor
      }

      val catalogFromCache = catalogManager.byCatalogDescriptor(catalogDescriptor)
      if (catalogFromCache != null) {
        return@Try catalogFromCache
      }

      val catalogFromDatabase = kurobaExLiteDatabase.call {
        val catalogEntity = chanCatalogDao.selectCatalog(
          siteKey = catalogDescriptor.siteKeyActual,
          boardCode = catalogDescriptor.boardCode
        )

        if (catalogEntity == null) {
          return@call null
        }

        return@call catalogEntity.toChanCatalog()
      }.unwrap()

      if (catalogFromDatabase != null) {
        catalogManager.insert(catalogFromDatabase)
      }

      return@Try catalogFromDatabase
    }
  }

}