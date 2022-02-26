package com.github.k1rakishou.kurobaexlite

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.MainScreen

class MainActivityViewModel : ViewModel() {

  val rootNavigationRouter = NavigationRouter(
    routerKey = MainScreen.SCREEN_KEY.key,
    routerIndex = null,
    parentRouter = null
  )

}