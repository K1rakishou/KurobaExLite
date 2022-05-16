package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor

data class SearchParams(
  val catalogDescriptor: CatalogDescriptor,
  val isSiteWideSearch: Boolean,
  val query: String,
  val page: Int
)

data class SearchResult(
  val foundPosts: List<IPostData>
)