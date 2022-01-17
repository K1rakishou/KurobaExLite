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

class AlbumScreen(componentActivity: ComponentActivity) : ComposeScreen(componentActivity) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Yellow.copy(alpha = 0.5f))
    ) {
      Text(text = "AlbumScreen")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("AlbumScreen")
  }
}