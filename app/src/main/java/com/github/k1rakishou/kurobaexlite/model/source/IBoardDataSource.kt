package com.github.k1rakishou.kurobaexlite.model.source

interface IBoardDataSource<Input, Output> {
  suspend fun loadBoards(input: Input): Result<Output>
}