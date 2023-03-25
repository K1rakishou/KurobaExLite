package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowRow
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable

private const val COIL_FAILED_TO_DECODE_FRAME_ERROR_MSG =
  "Often this means BitmapFactory could not decode the image data read from the input source"

@Composable
fun ReplyAttachments(
  replyLayoutState: ReplyLayoutState,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  val paddings = 8.dp
  val attachedMediaList = replyLayoutState.attachedMediaList
  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibilityState

  val additionalModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier.wrapContentHeight()
  } else {
    val scrollState = rememberScrollState()

    Modifier
      .height(90.dp)
      .verticalScroll(state = scrollState)
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .then(additionalModifier)
  ) {
    val mediaHeight = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) 120.dp else 80.dp

    val mediaWidth = if (this.maxWidth > 250.dp) {
      (this.maxWidth - paddings) / 2
    } else {
      this.maxWidth
    }

    FlowRow(
      modifier = Modifier.fillMaxSize(),
      mainAxisSpacing = 2.dp,
      crossAxisSpacing = 2.dp
    ) {
      attachedMediaList.forEach { attachedMedia ->
        key(attachedMedia.path) {
          AttachedMediaThumbnail(
            mediaWidth = mediaWidth,
            mediaHeight = mediaHeight,
            paddings = paddings,
            attachedMedia = attachedMedia,
            onAttachedMediaClicked = onAttachedMediaClicked,
            onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked
          )
        }
      }
    }
  }
}

@Composable
private fun AttachedMediaThumbnail(
  mediaWidth: Dp,
  mediaHeight: Dp,
  paddings: Dp,
  attachedMedia: AttachedMedia,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current

  val attachedMediaFile = attachedMedia.asFile
  val mediaHeightPx = with(density) { mediaHeight.roundToPx() - 8.dp.roundToPx() }

  Box(
    modifier = Modifier
      .width(mediaWidth)
      .height(mediaHeight)
  ) {
    val imageRequest = remember(key1 = attachedMediaFile, key2 = mediaHeightPx) {
      return@remember ImageRequest.Builder(context)
        .data(attachedMediaFile)
        .crossfade(true)
        .size(mediaHeightPx)
        .videoFrameMillis(frameMillis = 1000L)
        .build()
    }

    var imageStateMut by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val imageState = imageStateMut

    Box {
      AsyncImage(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = paddings / 2, vertical = paddings / 2)
          .kurobaClickable(onClick = { onAttachedMediaClicked(attachedMedia) }),
        model = imageRequest,
        contentDescription = "Attached media",
        contentScale = ContentScale.Crop,
        onState = { state -> imageStateMut = state }
      )

      if (imageState is AsyncImagePainter.State.Error) {
        logcatError {
          "AttachedMediaThumbnail() attachedMediaFilePath=${attachedMediaFile.path}, " +
            "error=${imageState.result.throwable.errorMessageOrClassName()}"
        }

        val isFailedToDecodeVideoFrameError = (imageState.result.throwable as? IllegalStateException)
          ?.message
          ?.contains(COIL_FAILED_TO_DECODE_FRAME_ERROR_MSG)
          ?: false

        val drawableId = if (isFailedToDecodeVideoFrameError) {
          R.drawable.ic_baseline_movie_24
        } else {
          R.drawable.ic_baseline_warning_24
        }

        KurobaComposeIcon(
          modifier = Modifier
            .size(24.dp)
            .align(Alignment.Center),
          drawableId = drawableId
        )
      }
    }

    val iconBgColor = remember { Color.Black.copy(alpha = 0.5f) }

    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(end = 4.dp, top = 4.dp)
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .background(
            color = iconBgColor,
            shape = CircleShape
          )
          .kurobaClickable(
            bounded = false,
            onClick = { onRemoveAttachedMediaClicked(attachedMedia) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )
    }
  }
}