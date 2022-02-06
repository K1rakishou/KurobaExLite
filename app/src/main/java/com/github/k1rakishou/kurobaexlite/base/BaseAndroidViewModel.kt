package com.github.k1rakishou.kurobaexlite.base

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.AndroidViewModel
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class BaseAndroidViewModel(
  private val application: KurobaExLiteApplication
) : AndroidViewModel(application) {
  protected val mainScope by lazy { MainScope() }

  protected val appContext: Context
    get() = application.applicationContext
  protected val resources: Resources
    get() = appContext.resources

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