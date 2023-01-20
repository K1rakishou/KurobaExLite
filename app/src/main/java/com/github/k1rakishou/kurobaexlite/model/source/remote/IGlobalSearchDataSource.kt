package com.github.k1rakishou.kurobaexlite.model.source.remote

interface IGlobalSearchDataSource<Input, Output> {
  suspend fun loadSearchPageData(input: Input): Result<Output>
}