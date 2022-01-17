package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class ThreadScreen(componentActivity: ComponentActivity) : ComposeScreen(componentActivity) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Blue.copy(alpha = 0.5f))
    ) {
      Text(text = "ThreadScreen")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}