package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import android.util.LruCache
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.IPostsState

private val toolbarTitleCache = LruCache<ChanDescriptor, String>(16)

@Composable
fun BoxScope.PostsScreenToolbar(
  isCatalogScreen: Boolean,
  postListAsync: AsyncData<IPostsState>,
  parsedPostDataCache: ParsedPostDataCache,
  onToolbarOverflowMenuClicked: (() -> Unit)? = null
) {
  val chanTheme = LocalChanTheme.current

  KurobaToolbarLayout(
    middlePart = {
      var toolbarTitle by remember {
        val chanDescriptor = (postListAsync as? AsyncData.Data)?.data?.chanDescriptor

        val initialValue = if (chanDescriptor != null) {
          toolbarTitleCache[chanDescriptor]
        } else {
          null
        }

        return@remember mutableStateOf<String?>(initialValue)
      }

      when (postListAsync) {
        AsyncData.Empty -> {
          // no-op
        }
        AsyncData.Loading -> toolbarTitle = stringResource(R.string.toolbar_loading_title)
        is AsyncData.Error -> toolbarTitle = stringResource(R.string.toolbar_loading_subtitle)
        is AsyncData.Data -> {
          LaunchedEffect(
            key1 = isCatalogScreen,
            key2 = postListAsync,
            block = {
              val postListState = postListAsync.data.posts.firstOrNull()
                ?: return@LaunchedEffect

              val originalPost by postListState
              val chanDescriptor = postListAsync.data.chanDescriptor

              toolbarTitle = parsedPostDataCache.formatToolbarTitle(
                chanDescriptor = chanDescriptor,
                postDescriptor = originalPost.postDescriptor,
                catalogMode = isCatalogScreen
              )

              toolbarTitleCache.put(chanDescriptor, toolbarTitle)
            })
        }
      }

      if (toolbarTitle != null) {
        Text(
          text = toolbarTitle!!,
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