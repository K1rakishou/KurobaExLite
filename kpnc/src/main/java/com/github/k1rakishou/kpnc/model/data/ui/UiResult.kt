package com.github.k1rakishou.kpnc.model.data.ui

sealed interface UiResult<out T> {
  object Empty : UiResult<Nothing>
  object Loading : UiResult<Nothing>
  data class Value<T>(val value: T) : UiResult<T>
  data class Error(val throwable: Throwable) : UiResult<Nothing>
}