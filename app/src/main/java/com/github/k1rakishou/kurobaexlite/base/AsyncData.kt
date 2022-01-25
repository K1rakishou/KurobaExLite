package com.github.k1rakishou.kurobaexlite.base

sealed class AsyncData<out T> {
  object Empty : AsyncData<Nothing>()
  object Loading : AsyncData<Nothing>()
  data class Error(val error: Throwable) : AsyncData<Nothing>()
  data class Data<T>(val data: T) : AsyncData<T>()
}