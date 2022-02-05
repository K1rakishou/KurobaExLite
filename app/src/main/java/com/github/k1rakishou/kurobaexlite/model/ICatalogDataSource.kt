package com.github.k1rakishou.kurobaexlite.model

interface ICatalogDataSource<Input, Output> {
  suspend fun loadCatalog(input: Input): Result<Output>
}