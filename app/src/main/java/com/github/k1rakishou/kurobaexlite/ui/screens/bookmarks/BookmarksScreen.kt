package com.github.k1rakishou.kurobaexlite.ui.screens.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey

class BookmarksScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    with(boxScope) {
      val chanTheme = LocalChanTheme.current

      KurobaToolbarLayout(
        middlePart = {
          KurobaComposeText(
            modifier = Modifier
              .wrapContentSize(),
            text = "Bookmarks",
            color = Color.White
          )
        },
        rightPart = {
          KurobaComposeIcon(
            modifier = Modifier
              .size(24.dp)
              .kurobaClickable(
                bounded = false,
                onClick = {
                  // TODO(KurobaEx):
                }
              ),
            drawableId = R.drawable.ic_baseline_more_vert_24,
            colorBehindIcon = chanTheme.primaryColorCompose
          )
        }
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
        .padding(top = toolbarHeight + windowInsets.top, bottom = windowInsets.bottom)
    ) {
      KurobaComposeText(
        modifier = Modifier
          .wrapContentSize(),
        text = "BookmarksScreen",
        color = Color.White
      )
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("BookmarksScreen")
  }
}