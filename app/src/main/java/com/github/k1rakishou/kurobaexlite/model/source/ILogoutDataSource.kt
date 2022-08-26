package com.github.k1rakishou.kurobaexlite.model.source

interface ILogoutDataSource<Input, Output> {
  suspend fun logout(input: Input): Result<Output>
}