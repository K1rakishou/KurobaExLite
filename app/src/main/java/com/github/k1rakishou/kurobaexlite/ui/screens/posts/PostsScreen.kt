package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {

  @Composable
  protected fun BoxScope.CatalogOrThreadLoadingIndicator(isCatalogMode: Boolean) {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val padding = 8.dp
    val bottomOffset = insets.bottomDp + padding

    KurobaComposeCardView(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .requiredHeightIn(12.dp, 100.dp)
        .offset(y = -bottomOffset)
        .padding(horizontal = padding)
        .align(Alignment.BottomCenter),
      backgroundColor = chanTheme.backColorSecondaryCompose
    ) {
      BoxWithConstraints {
        Row(
          modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          KurobaComposeText(
            modifier = Modifier
              .wrapContentHeight()
              .weight(1f),
            text = stringResource(R.string.posts_screen_processing_posts)
          )

          KurobaComposeLoadingIndicator(
            modifier = Modifier.wrapContentSize(),
            indicatorSize = 24.dp
          )
        }
      }
    }
  }

}