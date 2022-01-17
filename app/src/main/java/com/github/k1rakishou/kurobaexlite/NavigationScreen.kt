package com.github.k1rakishou.kurobaexlite

import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.screens.EmptyScreen
import com.github.k1rakishou.kurobaexlite.screens.MainScreen

typealias ScreenContentBuilder = @Composable () -> Unit

sealed class NavigationScreen {
  abstract val key: ScreenKey
  abstract val screenContentBuilder: ScreenContentBuilder

  class Empty(
    override val key: ScreenKey = ScreenKey("EmptyScreen"),
    override val screenContentBuilder: ScreenContentBuilder = { EmptyScreen() }
  ) : NavigationScreen()

  class Main(
    override val key: ScreenKey = ScreenKey("MainScreen"),
    override val screenContentBuilder: ScreenContentBuilder = { MainScreen() }
  ) : NavigationScreen()
}

inline class ScreenKey(val key: String)