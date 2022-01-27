package com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey

class BookmarksScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreenWithToolbar(componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Toolbar(boxScope: BoxScope) {

  }

  @Composable
  override fun Content() {
    InsetsAwareBox(
      modifier = Modifier
        .fillMaxSize()
    ) {
      Text(text = "BookmarksScreen")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("BookmarksScreen")
  }
}