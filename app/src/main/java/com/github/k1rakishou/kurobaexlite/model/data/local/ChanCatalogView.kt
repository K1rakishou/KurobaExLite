package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

data class ChanCatalogView(
  val catalogDescriptor: CatalogDescriptor,
  var lastViewedPostDescriptor: PostDescriptor? = null,
)