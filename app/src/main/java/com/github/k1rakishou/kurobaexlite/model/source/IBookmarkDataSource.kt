package com.github.k1rakishou.kurobaexlite.model.source

interface IBookmarkDataSource<Input, Output> {
  suspend fun loadBookmarkData(input: Input): Result<Output>
}