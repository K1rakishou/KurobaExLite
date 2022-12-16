package com.github.k1rakishou.kurobaexlite.features.boards

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey

data class ChanCatalogUiData(
  val siteKey: SiteKey,
  val catalogDescriptor: CatalogDescriptor,
  val title: String,
  val subtitle: String?
) {

  val boardCode: String
    get() = catalogDescriptor.boardCode

  fun matchesQuery(searchQuery: String): Boolean {
    if (searchQuery.startsWith('/') || searchQuery.endsWith('/')) {
      // User explicitly want to search only by the board code.
      return "/${catalogDescriptor.boardCode}/".contains(searchQuery, ignoreCase = true)
    }

    if (title.contains(searchQuery, ignoreCase = true)) {
      return true
    }

    if (subtitle != null && subtitle.contains(searchQuery, ignoreCase = true)) {
      return true
    }

    return false
  }
}