package com.github.k1rakishou.kurobaexlite.features.media.media_handlers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
    additionalPaddings = additionalPaddings
  ) {
    // TODO(KurobaEx):
    KurobaComposeText(text = "Error: media is not supported")
  }
}