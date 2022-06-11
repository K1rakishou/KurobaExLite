package com.github.k1rakishou.kurobaexlite.features.media.media_handlers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.media.ImageLoadState
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText

@Composable
fun DisplayUnsupportedMedia(
  isMinimized: Boolean,
  toolbarHeight: Dp,
  postImageDataLoadState: ImageLoadState.Ready,
  onFullImageLoaded: () -> Unit,
  onFullImageFailedToLoad: () -> Unit,
  onImageTapped: () -> Unit
) {
  if (isMinimized) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeIcon(
        modifier = Modifier.size(24.dp),
        drawableId = R.drawable.ic_baseline_warning_24
      )
    }

    return
  }

  val additionalPaddings = remember(toolbarHeight) { PaddingValues(top = toolbarHeight) }

  InsetsAwareBox(
    modifier = Modifier.fillMaxSize(),
    additionalPaddings = additionalPaddings
  ) {
    // TODO(KurobaEx):
    KurobaComposeText(text = "Error: media is not supported")
  }
}