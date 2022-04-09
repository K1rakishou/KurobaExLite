package com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.SCROLLBAR_MIN_SIZE
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScrollbarDimens
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.scrollbar
import com.github.k1rakishou.kurobaexlite.ui.screens.media.ImageLoadState

private const val TAG = "MediaViewerPreviewStrip"

@OptIn(ExperimentalPagerApi::class)
@Composable
fun MediaViewerPreviewStrip(
  pagerState: PagerState,
  images: List<ImageLoadState>,
  bgColor: Color,
  toolbarHeightPx: Int?,
  uiInfoManager: UiInfoManager,
  onPreviewClicked: (IPostImage) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val currentPage = pagerState.currentPage
  val thumbColor = chanTheme.accentColorCompose

  val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
  val itemSizeDp = if (uiInfoManager.isTablet) 64.dp else 42.dp
  val padding = if (uiInfoManager.isTablet) 4.dp else 2.dp
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
          ),
          scrollbarDragged = false
        )
        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
      state = lazyListState
    ) {
      items(
        count = images.size,
        key = { index -> images[index].fullImageUrlAsString },
        itemContent = { index ->
          val postImageData = images[index].postImageData

          DisplayImagePreview(
            itemSize = itemSizeDp,
            padding = padding,
            postImageData = postImageData,
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
  postImageData: IPostImage,
  isCurrentImage: Boolean,
  onPreviewClicked: (IPostImage) -> Unit
) {
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current
  val bgColor = chanTheme.backColorSecondaryCompose
  val highlightColor = chanTheme.accentColorCompose

  val highlightModifier = if (isCurrentImage) {
    Modifier.background(highlightColor)
  } else {
    Modifier
  }

  SubcomposeAsyncImage(
    modifier = Modifier
      .size(itemSize)
      .then(highlightModifier)
      .padding(padding)
      .background(bgColor)
      .kurobaClickable(onClick = { onPreviewClicked(postImageData) }),
    model = ImageRequest.Builder(context)
      .data(postImageData.thumbnailAsUrl)
      .crossfade(false)
      .build(),
    contentDescription = null,
    contentScale = ContentScale.Fit,
    content = {
      val state = painter.state
      if (state is AsyncImagePainter.State.Error) {
        logcatError(tag = TAG) {
          "DisplayImagePreview() url=${postImageData}, " +
            "postDescriptor=${postImageData.ownerPostDescriptor}, " +
            "error=${state.result.throwable}"
        }
      }

      SubcomposeAsyncImageContent()
    }
  )
}
