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
import androidx.compose.runtime.derivedStateOf
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
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostImageThumbnail
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.ScrollbarDimens
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.scrollbar

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
  val density = LocalDensity.current
  val currentPageIndex by remember { derivedStateOf { pagerState.currentPage } }

  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val lazyListState = rememberLazyListState()

  val itemSizeDp = if (globalUiInfoManager.isTablet) 64.dp else 42.dp
  val padding = if (globalUiInfoManager.isTablet) 4.dp else 2.dp
  val scrollbarHeight = with(density) { 4.dp.toPx().toInt() }

  val itemSizePx = with(density) {
    remember(key1 = itemSizeDp) { itemSizeDp.toPx().toInt() }
  }
  val scrollbarWidthPx = with(density) {
    remember { 42.dp.toPx().toInt() }
  }

  var stripWidthMut by remember { mutableStateOf<Int?>(null) }
  val stripWidth = stripWidthMut

  val scrollOffset = remember(key1 = stripWidth, key2 = itemSizePx) {
    if (stripWidth == null || stripWidth <= 0) {
      return@remember 0
    }

    return@remember (stripWidth - itemSizePx) / 2
  }

  if (stripWidth != null) {
    LaunchedEffect(
      key1 = Unit,
      block = { lazyListState.scrollToItem(currentPageIndex, -scrollOffset) }
    )

    LaunchedEffect(
      key1 = currentPageIndex,
      block = { lazyListState.animateScrollToItem(currentPageIndex, -scrollOffset) }
    )
  }

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
          scrollbarDimens = ScrollbarDimens.Horizontal.Static(
            height = scrollbarHeight,
            width = scrollbarWidthPx
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
            isCurrentImage = index == currentPageIndex,
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
  val highlightColor = chanTheme.accentColor

  val highlightModifier = if (isCurrentImage) {
    Modifier.background(highlightColor)
  } else {
    Modifier
  }

  PostImageThumbnail(
    modifier = Modifier
      .size(itemSize)
      .then(highlightModifier)
      .padding(padding),
    bgColor = Color.Unspecified,
    postImage = postImage,
    onClickWithError = { onPreviewClicked(postImage) }
  )
}
