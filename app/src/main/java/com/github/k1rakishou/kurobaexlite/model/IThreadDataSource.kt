package com.github.k1rakishou.kurobaexlite.model

interface IThreadDataSource<Input, Output> {
  suspend fun loadThread(input: Input): Result<Output>
}