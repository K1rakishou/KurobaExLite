package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme

@Composable
fun BoxScope.PostsScreenToolbar(
  isCatalogScreen: Boolean,
  postListAsync: AsyncData<List<PostData>>,
  onToolbarOverflowMenuClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val toolbarTitle = when (postListAsync) {
    AsyncData.Empty -> null
    AsyncData.Loading -> stringResource(R.string.toolbar_loading_title)
    is AsyncData.Error -> stringResource(R.string.toolbar_loading_subtitle)
    is AsyncData.Data -> {
      val originalPost = postListAsync.data.firstOrNull()
      remember(key1 = originalPost) { originalPost?.formatToolbarTitle(catalogMode = isCatalogScreen) }
    }
  }

  if (toolbarTitle.isNotNullNorBlank()) {
    Row(
      modifier = Modifier
        .wrapContentSize()
        .align(Alignment.CenterStart)
        .padding(horizontal = 4.dp)
    ) {
      Text(
        modifier = Modifier.weight(1f),
        text = toolbarTitle,
        color = Color.White,
        fontSize = 16.sp
      )

      Spacer(modifier = Modifier.width(4.dp))

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
  }
}

sealed class ToolbarMenuItem {

  data class TextMenu(
    val id: Int,
    @StringRes val textId: Int,
    @StringRes val subTextId: Int? = null
  ) : ToolbarMenuItem()

}