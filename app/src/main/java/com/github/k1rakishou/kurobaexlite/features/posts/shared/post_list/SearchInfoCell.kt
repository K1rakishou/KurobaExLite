package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme

internal val searchInfoCellHeight = 32.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.SearchInfoCell(
  cellsPadding: PaddingValues,
  contentPadding: PaddingValues,
  postsScreenViewModel: PostScreenViewModel,
  searchQuery: String?
) {
  if (searchQuery == null) {
    return
  }

  val chanTheme = LocalChanTheme.current
  val foundPostsCount = postsScreenViewModel.postScreenState.displayingPostsCount ?: 0

  val combinedPaddings = remember(key1 = cellsPadding) {
    PaddingValues(
      start = cellsPadding.calculateStartPadding(LayoutDirection.Ltr),
      end = cellsPadding.calculateEndPadding(LayoutDirection.Ltr),
      top = 4.dp
    )
  }

  val topOffset = remember(key1 = contentPadding) {
    contentPadding.calculateTopPadding()
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(searchInfoCellHeight)
      .padding(combinedPaddings)
      .align(Alignment.TopCenter)
      .offset(y = topOffset)
  ) {
    val context = LocalContext.current

    KurobaComposeCardView(
      backgroundColor = chanTheme.backColorSecondaryCompose
    ) {
      val text = remember(key1 = foundPostsCount, key2 = searchQuery) {
        context.resources.getString(
          R.string.search_hint,
          foundPostsCount,
          context.resources.getQuantityString(R.plurals.posts, foundPostsCount),
          searchQuery
        )
      }

      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp),
        text = text,
        color = chanTheme.textColorSecondaryCompose,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}