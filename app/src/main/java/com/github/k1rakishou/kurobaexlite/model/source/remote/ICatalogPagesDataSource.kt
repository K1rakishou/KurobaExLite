package com.github.k1rakishou.kurobaexlite.model.source.remote

interface ICatalogPagesDataSource<Input, Output> {
  suspend fun loadCatalogPagesData(input: Input): Result<Output>
}