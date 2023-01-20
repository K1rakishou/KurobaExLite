package com.github.k1rakishou.kurobaexlite.model.source.remote

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

interface IThreadDataSource<Input, Output> {
  suspend fun loadThread(input: Input, lastCachedThreadPost: PostDescriptor?): Result<Output>
}