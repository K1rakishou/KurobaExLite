package com.github.k1rakishou.kurobaexlite.base

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat

abstract class BaseViewModel : ViewModel() {
  init {
    viewModelScope.launch(Dispatchers.Main) { onViewModelReady() }
  }

  @CallSuper
  open suspend fun onViewModelReady() {
    logcat("BaseViewModel", LogPriority.VERBOSE) { "ViewModel ${this.javaClass.simpleName} created" }
  }

  fun forceInit() {
    // no-op
  }
}