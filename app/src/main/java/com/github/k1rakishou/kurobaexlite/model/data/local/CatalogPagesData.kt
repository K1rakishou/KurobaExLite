package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

class CatalogPagesData(
  val pagesTotal: Int,
  val pagesInfo: Map<ThreadDescriptor, Int>
)