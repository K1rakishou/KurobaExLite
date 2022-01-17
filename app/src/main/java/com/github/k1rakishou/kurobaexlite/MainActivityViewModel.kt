package com.github.k1rakishou.kurobaexlite

import androidx.lifecycle.ViewModel
import org.koin.java.KoinJavaComponent.inject

class MainActivityViewModel : ViewModel() {
  val navigation by inject<Navigation>(Navigation::class.java)

  init {
    navigation.pushScreen(NavigationScreen.Main())
  }

}