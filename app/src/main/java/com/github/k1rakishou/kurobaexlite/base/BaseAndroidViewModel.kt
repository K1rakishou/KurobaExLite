package com.github.k1rakishou.kurobaexlite.base

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseAndroidViewModel(
  private val application: KurobaExLiteApplication
) : AndroidViewModel(application) {
  protected val appContext: Context
    get() = application.applicationContext
  protected val resources: Resources
    get() = appContext.resources

  init {
    // We must use Dispatchers.Main here (without immediate) because otherwise all the injections won't
    // be executed in time and the app will crash
    viewModelScope.launch(Dispatchers.Main) { onViewModelReady() }
  }

  open suspend fun onViewModelReady() {

  }
}