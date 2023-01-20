package com.github.k1rakishou.kurobaexlite.model.source.remote

interface ICatalogDataSource<Input, Output> {
  suspend fun loadCatalog(input: Input): Result<Output>
}