package com.github.k1rakishou.kurobaexlite.base

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat

abstract class BaseViewModel : ViewModel() {
  protected val mainScope by lazy { MainScope() }

  init {
    mainScope.launch(Dispatchers.Main) { onViewModelReady() }
  }

  @CallSuper
  open suspend fun onViewModelReady() {
    logcat("BaseViewModel", LogPriority.VERBOSE) { "ViewModel ${this.javaClass.simpleName} created" }
  }

  override fun onCleared() {
    super.onCleared()

    mainScope.cancel()
  }
}