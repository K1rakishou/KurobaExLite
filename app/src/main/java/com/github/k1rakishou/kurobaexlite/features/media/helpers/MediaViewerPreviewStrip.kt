package com.github.k1rakishou.kurobaexlite.features.media.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.features.media.ImageLoadState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.ImageThumbnail
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.SCROLLBAR_MIN_SIZE
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScrollbarDimens
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.scrollbar

private const val TAG = "MediaViewerPreviewStrip"

@OptIn(ExperimentalPagerApi::class)
@Composable
fun MediaViewerPreviewStrip(
  pagerState: PagerState,
  images: List<ImageLoadState>,
  bgColor: Color,
  toolbarHeightPx: Int?,
  onPreviewClicked: (IPostImage) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val currentPage = pagerState.currentPage
  val thumbColor = chanTheme.accentColorCompose

  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

  val itemSizeDp = if (globalUiInfoManager.isTablet) 64.dp else 42.dp
  val padding = if (globalUiInfoManager.isTablet) 4.dp else 2.dp
  val scrollbarHeight = with(density) { 4.dp.toPx().toInt() }

  val itemSizePx = with(density) {
    remember(key1 = itemSizeDp) { itemSizeDp.toPx().toInt() }
  }
  val scrollbarMinWidthPx = with(density) {
    remember { SCROLLBAR_MIN_SIZE.toPx().toInt() }
  }

  var stripWidthMut by remember { mutableStateOf<Int?>(null) }
  val stripWidth = stripWidthMut

  val scrollOffset = remember(key1 = stripWidth, key2 = itemSizePx) {
    if (stripWidth == null || stripWidth <= 0) {
      return@remember 0
    }

    return@remember (stripWidth - itemSizePx) / 2
  }

  LaunchedEffect(
    key1 = currentPage,
    key2 = scrollOffset,
    block = { lazyListState.animateScrollToItem(currentPage, -scrollOffset) }
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .onSizeChanged { size -> stripWidthMut = size.width }
  ) {
    if (toolbarHeightPx != null) {
      val toolbarHeightDp = with(density) { remember(key1 = toolbarHeightPx) { toolbarHeightPx.toDp() } }
      Spacer(modifier = Modifier.height(toolbarHeightDp))
    }

    LazyRow(
      modifier = Modifier
        .height(itemSizeDp)
        .fillMaxWidth()
        .background(bgColor)
        .scrollbar(
          state = lazyListState,
          thumbColor = thumbColor,
          scrollbarDimens = ScrollbarDimens.Horizontal(
            height = scrollbarHeight,
            minWidth = scrollbarMinWidthPx
          )
        )
        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
      state = lazyListState
    ) {
      items(
        count = images.size,
        key = { index -> images[index].fullImageUrlAsString },
        itemContent = { index ->
          val postImage = images[index].postImage

          DisplayImagePreview(
            itemSize = itemSizeDp,
            padding = padding,
            postImage = postImage,
            isCurrentImage = index == currentPage,
            onPreviewClicked = onPreviewClicked
          )
        }
      )
    }
  }
}

@Composable
private fun DisplayImagePreview(
  itemSize: Dp,
  padding: Dp,
  postImage: IPostImage,
  isCurrentImage: Boolean,
  onPreviewClicked: (IPostImage) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val bgColor = chanTheme.backColorSecondaryCompose
  val highlightColor = chanTheme.accentColorCompose

  val highlightModifier = if (isCurrentImage) {
    Modifier.background(highlightColor)
  } else {
    Modifier
  }

  ImageThumbnail(
    modifier = Modifier
      .size(itemSize)
      .then(highlightModifier)
      .padding(padding)
      .background(bgColor)
      .kurobaClickable(onClick = { onPreviewClicked(postImage) }),
    postImage = postImage
  )
}
