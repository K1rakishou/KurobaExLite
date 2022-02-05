package com.github.k1rakishou.kurobaexlite.base

import androidx.lifecycle.AndroidViewModel
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class BaseAndroidViewModel(
  application: KurobaExLiteApplication
) : AndroidViewModel(application) {
  protected val mainScope by lazy { MainScope() }

  init {
    mainScope.launch(Dispatchers.Main) { onViewModelReady() }
  }

  open suspend fun onViewModelReady() {

  }

  override fun onCleared() {
    super.onCleared()

    mainScope.cancel()
  }
}