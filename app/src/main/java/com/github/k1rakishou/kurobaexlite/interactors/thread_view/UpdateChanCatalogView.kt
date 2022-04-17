package com.github.k1rakishou.kurobaexlite.interactors.thread_view

import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalogView
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class UpdateChanCatalogView(
  private val chanViewManager: ChanViewManager
) {

  suspend fun execute(
    catalogDescriptor: CatalogDescriptor,
    catalogBoundPostDescriptor: PostDescriptor?,
  ): ChanCatalogView? {
    return chanViewManager.insertOrUpdate(catalogDescriptor) {
      lastViewedPostDescriptor = catalogBoundPostDescriptor
    }
  }

}