package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor

data class BoardsData(
  val chanBoards: List<ChanBoard>
)

data class ChanBoard(
  val catalogDescriptor: CatalogDescriptor,
  val boardTitle: String?,
  val boardDescription: String?
)