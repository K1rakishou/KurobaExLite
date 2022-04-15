package com.github.k1rakishou.kurobaexlite.features.media.helpers

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.media.ImageLoadState
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable

@OptIn(ExperimentalPagerApi::class)
@Composable
fun MediaViewerActionStrip(
  imageLoadState: ImageLoadState,
  bgColor: Color,
  onDownloadButtonClicked: (IPostImage) -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(42.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier
        .fillMaxHeight()
        .wrapContentWidth()
    ) {
      IconButton(
        iconId = R.drawable.ic_baseline_download_24,
        bgColor = bgColor,
        onClick = { onDownloadButtonClicked(imageLoadState.postImage) }
      )
    }
  }
}

@Composable
private fun IconButton(
  @DrawableRes iconId: Int,
  bgColor: Color,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(42.dp)
      .padding(4.dp)
      .background(color = bgColor, shape = CircleShape)
      .kurobaClickable(bounded = false, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    KurobaComposeIcon(drawableId = iconId)
  }
}