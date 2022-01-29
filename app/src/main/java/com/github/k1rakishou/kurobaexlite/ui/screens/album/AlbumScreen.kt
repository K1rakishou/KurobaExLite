package com.github.k1rakishou.kurobaexlite.ui.screens.album

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey

class AlbumScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    InsetsAwareBox(
      modifier = Modifier
        .fillMaxSize()
    ) {
      Text(text = "AlbumScreen")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("AlbumScreen")
  }
}