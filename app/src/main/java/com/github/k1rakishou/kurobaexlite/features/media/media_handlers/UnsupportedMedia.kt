package com.github.k1rakishou.kurobaexlite.features.media.media_handlers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.media.ImageLoadState
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText

@Composable
fun DisplayUnsupportedMedia(
  toolbarHeight: Dp,
  postImageDataLoadState: ImageLoadState.Ready,
  onFullImageLoaded: () -> Unit,
  onFullImageFailedToLoad: () -> Unit,
  onImageTapped: () -> Unit
) {
  val additionalPaddings = remember(toolbarHeight) { PaddingValues(top = toolbarHeight) }

  InsetsAwareBox(
    modifier = Modifier.fillMaxSize(),
    additionalPaddings = additionalPaddings,
    contentAlignment = Alignment.Center
  ) {
    val text = stringResource(
      id = R.string.media_viewer_media_not_supported,
      postImageDataLoadState.postImage.fullImageAsString
    )

    KurobaComposeText(text = text)
  }
}