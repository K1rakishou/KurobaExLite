package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey

class DrawerScreen(componentActivity: ComponentActivity) : ComposeScreen(componentActivity) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Red.copy(alpha = 0.5f))
    ) {
      Text(text = "DrawerScreen")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("DrawerScreen")
  }
}