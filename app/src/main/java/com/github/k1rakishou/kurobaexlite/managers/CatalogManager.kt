package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CatalogManager {
  private val mutex = Mutex()

  private val siteCatalogDescriptors = mutableMapOf<SiteKey, MutableSet<CatalogDescriptor>>()
  private val catalogs = mutableMapOf<CatalogDescriptor, ChanCatalog>()

  suspend fun insert(chanCatalog: ChanCatalog) {
    insertMany(listOf(chanCatalog))
  }

  suspend fun insertMany(chanCatalogList: List<ChanCatalog>) {
    mutex.withLock {
      chanCatalogList.forEach { chanCatalog ->
        val siteCatalogDescriptors = siteCatalogDescriptors.getOrPut(
          key = chanCatalog.catalogDescriptor.siteKey,
          defaultValue = { mutableSetWithCap(64) }
        )

        siteCatalogDescriptors += chanCatalog.catalogDescriptor
        catalogs[chanCatalog.catalogDescriptor] = chanCatalog
      }
    }
  }

  suspend fun bySiteKey(siteKey: SiteKey): List<ChanCatalog> {
    return mutex.withLock {
      val resultList = mutableListOf<ChanCatalog>()

      siteCatalogDescriptors[siteKey]?.forEach { catalogDescriptor ->
        resultList += catalogs[catalogDescriptor]
          ?: return@forEach
      }

      return@withLock resultList
    }
  }

  suspend fun byCatalogDescriptor(catalogDescriptor: CatalogDescriptor): ChanCatalog? {
    return mutex.withLock { catalogs[catalogDescriptor] }
  }

}