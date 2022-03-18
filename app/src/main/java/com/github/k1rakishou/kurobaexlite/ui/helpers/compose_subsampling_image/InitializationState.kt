package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

sealed class InitializationState {
  object Uninitialized : InitializationState()
  data class Error(val exception: Throwable) : InitializationState()
  object Success : InitializationState()
}