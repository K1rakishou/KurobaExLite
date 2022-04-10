package com.github.k1rakishou.kurobaexlite.ui.activity

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter

class MainActivityViewModel : ViewModel() {
  val rootNavigationRouter = MainNavigationRouter()
}