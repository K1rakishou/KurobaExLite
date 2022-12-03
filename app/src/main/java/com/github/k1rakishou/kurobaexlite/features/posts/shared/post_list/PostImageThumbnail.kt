package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.RevealedSpoilerImages
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ImageType
import com.github.k1rakishou.kurobaexlite.model.data.imageType
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.flow.takeWhile

@Composable
fun PostImageThumbnail(
  modifier: Modifier = Modifier,
  bgColor: Color = LocalChanTheme.current.backColorSecondary,
  showShimmerEffectWhenLoading: Boolean = false,
  contentScale: ContentScale = ContentScale.Fit,
  postImage: IPostImage,
  onClickWithError: ((Result<IPostImage>) -> Unit)? = null,
  onClick: (IPostImage) -> Unit = {},
  onLongClick: (IPostImage) -> Unit = {},
) {
  val context = LocalContext.current

  val revealedSpoilerImages = koinRemember<RevealedSpoilerImages>()
  val siteManager = koinRemember<SiteManager>()

  var loadErrorMut by remember { mutableStateOf<Throwable?>(null) }
  val loadError = loadErrorMut

  var revealSpoiler by remember { mutableStateOf(revealedSpoilerImages.isSpoilerImageRevealed(postImage.fullImageAsUrl)) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      revealedSpoilerImages.spoilerImageRevealedEvents
        .takeWhile { !revealSpoiler }
        .collect { imageFullUrl ->
          if (imageFullUrl == postImage.fullImageAsUrl) {
            revealSpoiler = true
          }
        }
    }
  )

  BoxWithConstraints(
    modifier = Modifier
      .background(bgColor)
      .kurobaClickable(
        onLongClick = { onLongClick(postImage) },
        onClick = {
          if (onClickWithError != null) {
            val result = if (loadError != null) {
              Result.failure(loadError)
            } else {
              Result.success(postImage)
            }

            onClickWithError(result)
          } else {
            onClick(postImage)
          }
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

    val thumbnailUrl = remember(key1 = revealSpoiler) {
      if (!revealSpoiler && postImage.thumbnailSpoiler != null) {
        val spoilerUrl = postImage.thumbnailSpoiler!!.spoilerThumbnailUrl(postImage.ownerPostDescriptor.catalogDescriptor)
        if (spoilerUrl != null) {
          return@remember spoilerUrl
        }
      }

      return@remember postImage.thumbnailAsUrl
    }

    val imageRequest by produceState<ImageRequest?>(
      initialValue = null,
      key1 = thumbnailUrl,
      producer = {
        value = ImageRequest.Builder(context)
          .data(thumbnailUrl)
          .crossfade(true)
          .size(Size.ORIGINAL)
          .also { imageRequestBuilder ->
            siteManager.bySiteKey(postImage.ownerPostDescriptor.siteKey)
              ?.requestModifier()
              ?.modifyCoilImageRequest(thumbnailUrl, imageRequestBuilder)
          }
          .build()
      }
    )

    Box(modifier = modifier) {
      if (imageRequest != null) {
        SubcomposeAsyncImage(
          modifier = Modifier.fillMaxSize(),
          model = imageRequest,
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
              Shimmer(modifier = Modifier.fillMaxSize())
            }

            if (state is AsyncImagePainter.State.Success) {
              SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
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
  }
}