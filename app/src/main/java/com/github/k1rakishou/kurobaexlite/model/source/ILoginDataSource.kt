package com.github.k1rakishou.kurobaexlite.model.source

interface ILoginDataSource<Input, Output> {
  suspend fun login(input: Input): Result<Output>
}