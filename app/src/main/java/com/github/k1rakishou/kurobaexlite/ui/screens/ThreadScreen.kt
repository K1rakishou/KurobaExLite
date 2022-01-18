package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey

class ThreadScreen(componentActivity: ComponentActivity) : ComposeScreen(componentActivity) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    InsetsAwareBox(
      modifier = Modifier
        .fillMaxSize()
    ) {
      Text(text = "ThreadScreen")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThreadScreen")
  }
}