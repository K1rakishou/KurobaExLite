package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.IPostsState

@Composable
fun BoxScope.PostsScreenToolbar(
  isCatalogScreen: Boolean,
  postListAsync: AsyncData<IPostsState>,
  onToolbarOverflowMenuClicked: (() -> Unit)? = null
) {
  val chanTheme = LocalChanTheme.current

  KurobaToolbarLayout(
    middlePart = {
      val toolbarTitle = when (postListAsync) {
        AsyncData.Empty -> null
        AsyncData.Loading -> stringResource(R.string.toolbar_loading_title)
        is AsyncData.Error -> stringResource(R.string.toolbar_loading_subtitle)
        is AsyncData.Data -> {
          val postListState = postListAsync.data.posts.firstOrNull()
          if (postListState != null) {
            val originalPost by postListState

            remember(key1 = originalPost) {
              originalPost.formatToolbarTitle(catalogMode = isCatalogScreen)
            }
          } else {
            null
          }
        }
      }

      if (toolbarTitle != null) {
        Text(
          text = toolbarTitle,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 16.sp
        )
      }
    },
    rightIcons = {
      KurobaComposeIcon(
        modifier = Modifier
          .size(24.dp)
          .kurobaClickable(
            bounded = false,
            onClick = onToolbarOverflowMenuClicked
          ),
        drawableId = R.drawable.ic_baseline_more_vert_24,
        colorBehindIcon = chanTheme.primaryColorCompose
      )
    }
  )
}

sealed class ToolbarMenuItem {
  abstract val menuItemId: Int

  data class TextMenu(
    override val menuItemId: Int,
    @StringRes val textId: Int,
    @StringRes val subTextId: Int? = null
  ) : ToolbarMenuItem()

}