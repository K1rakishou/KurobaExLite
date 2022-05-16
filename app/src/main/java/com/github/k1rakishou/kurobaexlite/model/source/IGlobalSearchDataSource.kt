package com.github.k1rakishou.kurobaexlite.model.source

interface IGlobalSearchDataSource<Input, Output> {
  suspend fun loadSearchPageData(input: Input): Result<Output>
}