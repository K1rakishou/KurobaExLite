package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable

@Composable
fun ReplyAttachments(
  height: Dp,
  replyLayoutVisibility: ReplyLayoutVisibility,
  attachedMediaList: List<AttachedMedia>,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  val paddings = 8.dp
  val mediaHeight = when (replyLayoutVisibility) {
    ReplyLayoutVisibility.Closed,
    ReplyLayoutVisibility.Opened -> height
    ReplyLayoutVisibility.Expanded -> height / 2
  }

  LazyVerticalGrid(
    modifier = Modifier
      .fillMaxWidth()
      .height(height),
    columns = GridCells.Fixed(3),
    content = {
      attachedMediaList.forEach { attachedMedia ->
        item(
          key = attachedMedia.path,
          content = {
            AttachedMediaThumbnail(
              mediaHeight = mediaHeight,
              paddings = paddings,
              attachedMedia = attachedMedia,
              replyLayoutVisibility = replyLayoutVisibility,
              onAttachedMediaClicked = onAttachedMediaClicked,
              onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked
            )
          }
        )
      }
    }
  )
}

@Composable
private fun AttachedMediaThumbnail(
  mediaHeight: Dp,
  paddings: Dp,
  attachedMedia: AttachedMedia,
  replyLayoutVisibility: ReplyLayoutVisibility,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  val context = LocalContext.current
  val attachedMediaFile = attachedMedia.asFile

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(mediaHeight)
  ) {
    SubcomposeAsyncImage(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = paddings / 2, vertical = paddings / 2)
        .kurobaClickable(onClick = { onAttachedMediaClicked(attachedMedia) }),
      model = ImageRequest.Builder(context)
        .data(attachedMediaFile)
        .crossfade(true)
        .build(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      content = {
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
          logcatError {
            "AttachedMediaThumbnail() attachedMediaFilePath=${attachedMediaFile.path}, " +
              "error=${state.result.throwable.errorMessageOrClassName()}"
          }

          KurobaComposeIcon(
            modifier = Modifier
              .size(24.dp)
              .align(Alignment.Center),
            drawableId = R.drawable.ic_baseline_warning_24
          )

          return@SubcomposeAsyncImage
        }

        SubcomposeAsyncImageContent()
      }
    )

    if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
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
}