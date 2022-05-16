package com.github.k1rakishou.kurobaexlite.model.repoository

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchParams
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchResult
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor

class GlobalSearchRepository(
  private val siteManager: SiteManager,
) {

  suspend fun getPage(
    catalogDescriptor: CatalogDescriptor,
    isSiteWideSearch: Boolean,
    searchQuery: String?,
    pageIndex: Int
  ): Result<SearchResult> {
    return Result.Try {
      if (searchQuery == null) {
        return@Try SearchResult(emptyList())
      }

      val site = siteManager.bySiteKey(catalogDescriptor.siteKey)
      if (site == null) {
        logcatError(TAG) { "Site '${catalogDescriptor.siteKeyActual}' is not supported" }
        return@Try SearchResult(emptyList())
      }

      val globalSearchInfo = site.globalSearchInfo()
      if (globalSearchInfo == null) {
        logcatError(TAG) { "Site '${catalogDescriptor.siteKeyActual}' does not support global search" }
        return@Try SearchResult(emptyList())
      }

      try {
        val searchParams = SearchParams(
          catalogDescriptor = catalogDescriptor,
          isSiteWideSearch = isSiteWideSearch,
          query = searchQuery,
          page = pageIndex
        )

        val searchResult = globalSearchInfo.globalSearchDataSource().loadSearchPageData(searchParams)
          .onFailure { error -> logcatError(TAG) {
            "loadSearchPageData($searchParams) error: ${error.asLogIfImportantOrErrorMessage()}" }
          }
          .unwrap()

        return@Try searchResult
      } catch (error: Throwable) {
        throw error
      }
    }
  }

  companion object {
    private const val TAG = "GlobalSearchRepository"
  }

}