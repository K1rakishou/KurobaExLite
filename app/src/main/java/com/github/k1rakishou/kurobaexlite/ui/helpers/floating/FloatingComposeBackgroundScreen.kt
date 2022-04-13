package com.github.k1rakishou.kurobaexlite.ui.helpers.floating

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks

class FloatingComposeBackgroundScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {

  private val backgroundColor = Color(
    red = 0f,
    green = 0f,
    blue = 0f,
    alpha = 0.6f
  )

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(backgroundColor)
        .consumeClicks(consume = true)
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("FloatingComposeBackgroundScreen")
  }

}