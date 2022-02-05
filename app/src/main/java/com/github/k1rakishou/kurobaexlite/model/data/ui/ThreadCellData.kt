package com.github.k1rakishou.kurobaexlite.model.data.ui

data class ThreadCellData(
  val totalReplies: Int = 0,
  val totalImages: Int = 0,
  val totalPosters: Int = 0,
  val lastLoadError: Throwable? = null
)