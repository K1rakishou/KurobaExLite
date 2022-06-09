package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberShimmerState

@Composable
fun ImageThumbnail(
  modifier: Modifier = Modifier,
  bgColor: Color = LocalChanTheme.current.backColorSecondary,
  showShimmerEffectWhenLoading: Boolean = false,
  contentScale: ContentScale = ContentScale.Fit,
  postImage: IPostImage,
  onClick: (Result<IPostImage>) -> Unit
) {
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current

  var loadErrorMut by remember { mutableStateOf<Throwable?>(null) }
  val loadError = loadErrorMut

  BoxWithConstraints(
    modifier = Modifier
      .background(bgColor)
      .then(modifier)
      .kurobaClickable(
        onClick = {
          val result = if (loadError != null) {
            Result.failure(loadError)
          } else {
            Result.success(postImage)
          }

          onClick(result)
        }
      )
  ) {
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

    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(context)
        .data(postImage.thumbnailAsUrl)
        .crossfade(true)
        .size(Size.ORIGINAL)
        .build(),
      contentDescription = null,
      contentScale = contentScale,
      onState = { state ->
        loadErrorMut = if (state is AsyncImagePainter.State.Error) {
          state.result.throwable
        } else {
          null
        }
      },
      content = {
        val state = painter.state

        if (
          (state is AsyncImagePainter.State.Empty || state is AsyncImagePainter.State.Loading)
          && showShimmerEffectWhenLoading
        ) {
          Shimmer(shimmerState = rememberShimmerState(bgColor = chanTheme.backColor))
        }

        if (state !is AsyncImagePainter.State.Error) {
          SubcomposeAsyncImageContent()
        }

        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          if (state is AsyncImagePainter.State.Error) {
            logcatError {
              "PostCellTitle() url=${postImage.thumbnailAsUrl}, " +
                "postDescriptor=${postImage.ownerPostDescriptor}, " +
                "error=${state.result.throwable.errorMessageOrClassName()}"
            }

            KurobaComposeIcon(
              modifier = Modifier.size(iconWidthDp, iconHeightDp),
              drawableId = R.drawable.ic_baseline_warning_24
            )
          } else {
            if (postImage.imageType() == ImageType.Video) {
              KurobaComposeIcon(
                modifier = Modifier.size(iconWidthDp, iconHeightDp),
                drawableId = R.drawable.ic_play_circle_outline_white_24dp
              )
            }
          }
        }
      }
    )
  }
}