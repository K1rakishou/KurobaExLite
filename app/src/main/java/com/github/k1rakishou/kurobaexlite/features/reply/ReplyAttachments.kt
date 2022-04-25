package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun ReplyAttachments(
  height: Dp,
  attachedMediaList: List<AttachedMedia>,
  replyLayoutVisibility: ReplyLayoutVisibility,
) {
  when (replyLayoutVisibility) {
    ReplyLayoutVisibility.Closed,
    ReplyLayoutVisibility.Opened -> {
      ReplyAttachmentsOpened(
        height = height,
        attachedMediaList = attachedMediaList,
      )
    }
    ReplyLayoutVisibility.Expanded -> {
      ReplyAttachmentsExpanded(
        height = height,
        attachedMediaList = attachedMediaList,
      )
    }
  }
}

@Composable
fun ReplyAttachmentsExpanded(
  height: Dp,
  attachedMediaList: List<AttachedMedia>
) {
  val mediaSize = remember { height - 8.dp } // TODO(KurobaEx): percent calculation

  LazyHorizontalGrid(
    modifier = Modifier
      .fillMaxWidth()
      .height(height),
    rows = GridCells.Adaptive(mediaSize),
    content = {
      attachedMediaList.forEach { attachedMedia ->
        item(
          key = attachedMedia.path,
          content = { AttachedMediaThumbnail(attachedMedia) }
        )
      }
    }
  )
}

@Composable
fun ReplyAttachmentsOpened(
  height: Dp,
  attachedMediaList: List<AttachedMedia>
) {
  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .height(height),
    content = {
      attachedMediaList.forEach { attachedMedia ->
        item(
          key = attachedMedia.path,
          content = { AttachedMediaThumbnail(attachedMedia) }
        )
      }
    }
  )
}

@Composable
private fun AttachedMediaThumbnail(
  attachedMedia: AttachedMedia
) {
  val context = LocalContext.current
  val attachedMediaFile = attachedMedia.asFile

  SubcomposeAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = ImageRequest.Builder(context)
      .data(attachedMediaFile)
      .crossfade(true)
      .build(),
    contentDescription = null,
    contentScale = ContentScale.Fit,
    content = {
      val state = painter.state

      if (state is AsyncImagePainter.State.Error) {
        logcatError {
          "PostCellTitle() attachedMediaFilePath=${attachedMediaFile.path}, " +
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
}