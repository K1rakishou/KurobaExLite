package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor

data class CatalogData(
  val catalogDescriptor: CatalogDescriptor,
  val catalogThreads: List<PostData>
)