package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon

@Composable
fun ImageThumbnail(
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Fit,
  postImage: IPostImage,
) {
  val context = LocalContext.current

  BoxWithConstraints(modifier = modifier) {
    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(context)
        .data(postImage.thumbnailAsUrl)
        .crossfade(true)
        .build(),
      contentDescription = null,
      contentScale = contentScale,
      content = {
        val state = painter.state
        if (state is AsyncImagePainter.State.Error) {
          logcatError {
            "PostCellTitle() url=${postImage.thumbnailAsUrl}, " +
              "postDescriptor=${postImage.ownerPostDescriptor}, " +
              "error=${state.result.throwable}"
          }
        }

        SubcomposeAsyncImageContent()
      }
    )

    if (postImage.imageType() == ImageType.Video) {
      val density = LocalDensity.current
      val desiredSizePx = with(density) { remember { 24.dp.roundToPx() } }

      val iconHeightDp = with(density) {
        remember(key1 = constraints.maxHeight) {
          desiredSizePx.coerceAtMost(constraints.maxHeight).toDp()
        }
      }
      val iconWidthDp = with(density) {
        remember(key1 = constraints.maxWidth) {
          desiredSizePx.coerceAtMost(constraints.maxWidth).toDp()
        }
      }

      KurobaComposeIcon(
        modifier = Modifier
          .size(iconWidthDp, iconHeightDp)
          .align(Alignment.Center),
        drawableId = R.drawable.ic_play_circle_outline_white_24dp
      )
    }
  }
}