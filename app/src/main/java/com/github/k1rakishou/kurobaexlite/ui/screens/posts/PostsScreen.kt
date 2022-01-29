package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeNavigationScreen

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean,
) : HomeNavigationScreen(componentActivity, navigationRouter, isStartScreen) {

  abstract val isCatalogScreen: Boolean

  @Composable
  abstract fun postDataAsync(): AsyncData<List<PostData>>

  @Composable
  protected fun BoxScope.CatalogOrThreadLoadingIndicator() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val padding = 8.dp
    val bottomOffset = insets.bottomDp + padding
    val postsCount = (postDataAsync() as? AsyncData.Data)?.data?.size?.toString() ?: "???"

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
            text = stringResource(R.string.posts_screen_processing_posts, postsCount)
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