package com.github.k1rakishou.kurobaexlite.ui.activity

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.main.MainScreen

class MainActivityViewModel : ViewModel() {

  val rootNavigationRouter = NavigationRouter(
    routerKey = MainScreen.SCREEN_KEY.key,
    parentRouter = null
  )

}