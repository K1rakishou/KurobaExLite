package com.github.k1rakishou.kurobaexlite.model.source.remote

interface IBookmarkDataSource<Input, Output> {
  suspend fun loadBookmarkData(input: Input): Result<Output>
}