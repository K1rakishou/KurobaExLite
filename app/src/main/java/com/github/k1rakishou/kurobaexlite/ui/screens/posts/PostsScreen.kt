package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreenWithToolbar

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreenWithToolbar(componentActivity, navigationRouter) {

  abstract val isCatalogScreen: Boolean

  @Composable
  abstract fun postDataAsync(): AsyncData<List<PostData>>

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val chanTheme = LocalChanTheme.current

    val toolbarTitle = when (val postListAsync = postDataAsync()) {
      AsyncData.Empty -> null
      AsyncData.Loading -> stringResource(R.string.toolbar_loading_title)
      is AsyncData.Error -> stringResource(R.string.toolbar_loading_subtitle)
      is AsyncData.Data -> {
        val originalPost = postListAsync.data.firstOrNull()
        remember(key1 = originalPost) { originalPost?.formatToolbarTitle(catalogMode = isCatalogScreen) }
      }
    }

    if (toolbarTitle.isNotNullNorBlank()) {
      with(boxScope) {
        Text(
          modifier = Modifier
            .wrapContentSize(),
          text = toolbarTitle,
          color = Color.White
        )
      }
    }
  }

  @Composable
  protected fun BoxScope.CatalogOrThreadLoadingIndicator() {
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