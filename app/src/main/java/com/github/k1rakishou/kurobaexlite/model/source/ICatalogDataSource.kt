package com.github.k1rakishou.kurobaexlite.model.source

interface ICatalogDataSource<Input, Output> {
  suspend fun loadCatalog(input: Input): Result<Output>
}