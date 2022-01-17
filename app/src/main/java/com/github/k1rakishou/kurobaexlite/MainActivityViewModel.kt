package com.github.k1rakishou.kurobaexlite

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

class MainActivityViewModel : ViewModel() {

  val rootNavigationRouter = NavigationRouter(
    routerIndex = null,
    parentRouter = null
  )

}