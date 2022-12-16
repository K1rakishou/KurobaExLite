package com.github.k1rakishou.kurobaexlite.interactors.catalog

import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey

class LoadCatalogsForAllSites(
  private val siteManager: SiteManager,
  private val retrieveSiteCatalogList: RetrieveSiteCatalogList
) {

  suspend fun await(): Map<SiteKey, List<ChanCatalog>> {
    val resultMap = mutableMapOf<SiteKey, List<ChanCatalog>>()

    siteManager.iterateSites { site ->
      val siteEnabled = site.siteSettings.siteEnabled.read()
      if (!siteEnabled) {
        return@iterateSites
      }

      val siteKey = site.siteKey

      val catalogList = retrieveSiteCatalogList.await(
        siteKey = siteKey,
        forceReload = false,
        networkLoadAllowed = false
      ).getOrNull()
        ?: return@iterateSites

      resultMap[siteKey] = catalogList
    }

    return resultMap
  }

}