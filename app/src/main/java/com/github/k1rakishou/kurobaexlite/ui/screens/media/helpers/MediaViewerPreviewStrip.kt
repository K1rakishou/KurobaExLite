package com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.SCROLLBAR_MIN_HEIGHT
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.simpleVerticalScrollbar
import com.github.k1rakishou.kurobaexlite.ui.screens.media.ImageLoadState

private const val TAG = "MediaViewerPreviewStrip"

@OptIn(ExperimentalPagerApi::class)
@Composable
fun MediaViewerPreviewStrip(
  pagerState: PagerState,
  images: List<ImageLoadState>,
  uiInfoManager: UiInfoManager,
  onPreviewClicked: (IPostImage) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val currentPage = pagerState.currentPage
  val thumbColor = chanTheme.accentColorCompose

  val maxParentHeight by uiInfoManager.maxParentHeightState
  val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

  val maxHeightDefaultDp = if (uiInfoManager.isTablet) 360.dp else 270.dp
  val itemSizeDp = if (uiInfoManager.isTablet) 64.dp else 42.dp
  val padding = if (uiInfoManager.isTablet) 4.dp else 2.dp

  val itemSizePx = with(LocalDensity.current) { remember(key1 = itemSizeDp) { itemSizeDp.toPx().toInt() } }
  val scrollbarWidth = with(LocalDensity.current) { 4.dp.toPx().toInt() }
  val scrollbarMinHeightPx = with(LocalDensity.current) { remember { SCROLLBAR_MIN_HEIGHT.toPx().toInt() } }
  val maxHeightDefaultPx = with(LocalDensity.current) { maxHeightDefaultDp.toPx().toInt() }
  val maxStripHeight = with(LocalDensity.current) {
    remember(key1 = maxParentHeight) { maxHeightDefaultPx.coerceAtMost(maxParentHeight).toDp() }
  }

  val bgColor = remember(key1 = chanTheme.backColorCompose) { chanTheme.backColorCompose.copy(alpha = 0.6f) }
  val scrollOffset = remember(key1 = maxHeightDefaultPx, key2 = itemSizePx) { (maxHeightDefaultPx - itemSizePx) / 2 }

  LaunchedEffect(
    key1 = currentPage,
    block = { lazyListState.animateScrollToItem(currentPage, -scrollOffset) }
  )

  LazyColumn(
    modifier = Modifier
      .width(itemSizeDp)
      .heightIn(max = maxStripHeight)
      .background(bgColor)
      .simpleVerticalScrollbar(
        state = lazyListState,
        thumbColor = thumbColor,
        scrollbarWidth = scrollbarWidth,
        scrollbarMinHeight = scrollbarMinHeightPx,
        scrollbarDragged = false
      )
      .padding(end = 4.dp),
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
