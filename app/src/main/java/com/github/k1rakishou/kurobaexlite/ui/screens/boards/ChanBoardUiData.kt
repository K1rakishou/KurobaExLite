package com.github.k1rakishou.kurobaexlite.ui.screens.boards

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor

data class ChanBoardUiData(
  val catalogDescriptor: CatalogDescriptor,
  val title: String,
  val subtitle: String?
) {
  fun matchesQuery(searchQuery: String): Boolean {
    if (title.contains(searchQuery, ignoreCase = true)) {
      return true
    }

    if (subtitle != null && subtitle.contains(searchQuery, ignoreCase = true)) {
      return true
    }

    return false
  }
}