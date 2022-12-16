package com.github.k1rakishou.kurobaexlite.base

import androidx.compose.runtime.Stable

@Stable
sealed class AsyncData<out T> {
  object Uninitialized : AsyncData<Nothing>()
  object Loading : AsyncData<Nothing>()
  data class Error(val error: Throwable) : AsyncData<Nothing>()
  data class Data<T>(val data: T) : AsyncData<T>()
}

fun <T> T.asAsyncData(): AsyncData.Data<T> = AsyncData.Data(this)