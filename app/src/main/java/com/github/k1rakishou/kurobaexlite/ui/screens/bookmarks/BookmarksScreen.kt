package com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey

class BookmarksScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean
) : HomeNavigationScreen(componentActivity, navigationRouter, isStartScreen) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    with(boxScope) {
      KurobaComposeText(
        modifier = Modifier
          .wrapContentSize(),
        text = "Bookmarks",
        color = Color.White
      )
    }
  }

  @Composable
  override fun Content() {
    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = toolbarHeight + windowInsets.topDp, bottom = windowInsets.bottomDp)
    ) {
      Text(text = "BookmarksScreen")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("BookmarksScreen")
  }
}