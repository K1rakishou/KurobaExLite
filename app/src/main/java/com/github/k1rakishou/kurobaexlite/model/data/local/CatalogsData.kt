package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor

data class CatalogsData(
  val chanCatalogs: List<ChanCatalog>
)

data class ChanCatalog(
  val catalogDescriptor: CatalogDescriptor,
  val boardTitle: String?,
  val boardDescription: String?,
  val workSafe: Boolean
)