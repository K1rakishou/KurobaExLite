package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.network.HttpException
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeThemeDependantText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberKurobaTextUnit
import kotlinx.coroutines.flow.takeWhile

@Composable
fun PostImageThumbnail(
  modifier: Modifier = Modifier,
  bgColor: Color = LocalChanTheme.current.backColorSecondary,
  displayErrorMessage: Boolean = true,
  showShimmerEffectWhenLoading: Boolean = false,
  contentScale: ContentScale = ContentScale.Fit,
  postImage: IPostImage,
  onClick: (IPostImage) -> Unit = {},
  onLongClick: (IPostImage) -> Unit = {},
) {
  val context = LocalContext.current

  val revealedSpoilerImages = koinRemember<RevealedSpoilerImages>()
  val siteManager = koinRemember<SiteManager>()

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

  var maxParentWidth by remember { mutableStateOf(0) }
  var maxParentHeight by remember { mutableStateOf(0) }

  Box(
    modifier = Modifier
      .background(bgColor)
      .onGloballyPositioned { layoutCoordinates ->
        maxParentWidth = layoutCoordinates.size.width
        maxParentHeight = layoutCoordinates.size.height
      }
      .kurobaClickable(
        onLongClick = { onLongClick(postImage) },
        onClick = { onClick(postImage) }
      )
      .then(modifier)
  ) {
    val density = LocalDensity.current

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

    if (imageRequest != null) {
      var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

      AsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = imageRequest,
        contentDescription = "Post image thumbnail",
        contentScale = contentScale,
        onState = { state -> imageState = state }
      )

      ImageOverlay(
        modifier = Modifier.fillMaxSize(),
        imageState = imageState,
        postImage = postImage,
        maxParentWidth = maxParentWidth,
        maxParentHeight = maxParentHeight,
        displayErrorMessage = displayErrorMessage,
        showShimmerEffectWhenLoading = showShimmerEffectWhenLoading
      )
    }
  }
}

@Composable
fun ImageOverlay(
  modifier: Modifier = Modifier,
  imageState: AsyncImagePainter.State,
  postImage: IPostImage,
  maxParentWidth: Int,
  maxParentHeight: Int,
  displayErrorMessage: Boolean,
  showShimmerEffectWhenLoading: Boolean
) {
  val density = LocalDensity.current

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    if (imageState is AsyncImagePainter.State.Success) {
      if (postImage.imageType() == ImageType.Video) {
        KurobaComposeIcon(
          modifier = Modifier.requiredSize(32.dp),
          drawableId = R.drawable.ic_play_circle_outline_white_24dp,
          iconColor = Color.White
        )
      }
    } else if (imageState is AsyncImagePainter.State.Error) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        LaunchedEffect(
          key1 = imageState,
          block = {
            logcatError {
              "PostImageThumbnail() url=${postImage.thumbnailAsUrl}, " +
                "postDescriptor=${postImage.ownerPostDescriptor}, " +
                "error=${imageState.result.throwable.errorMessageOrClassName()}"
            }
          }
        )

        PostImageThumbnailError(
          displayErrorMessage = displayErrorMessage,
          state = imageState,
          density = density,
          maxParentWidth = maxParentWidth,
          maxParentHeight = maxParentHeight
        )
      }
    } else if (
      (imageState is AsyncImagePainter.State.Empty || imageState is AsyncImagePainter.State.Loading)
      && showShimmerEffectWhenLoading
    ) {
      Shimmer(modifier = Modifier.fillMaxSize())
    }
  }
}

@Composable
private fun PostImageThumbnailError(
  displayErrorMessage: Boolean,
  state: AsyncImagePainter.State.Error,
  density: Density,
  maxParentWidth: Int,
  maxParentHeight: Int
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    KurobaComposeIcon(
      modifier = Modifier.size(32.dp),
      drawableId = R.drawable.ic_baseline_warning_24
    )

    val errorText = remember(key1 = state.result.throwable, key2 = displayErrorMessage) {
      if (!displayErrorMessage) {
        return@remember null
      }

      val minHeight = with(density) { 24.dp.roundToPx() }
      val minWidth = with(density) { 42.dp.roundToPx() }

      if (maxParentHeight < minHeight || maxParentWidth < minWidth) {
        // Not enough width or height to display the error message
        return@remember null
      }

      return@remember (state.result.throwable as? HttpException)
        ?.response
        ?.let { response -> "${response.code}" }
    }

    if (errorText != null) {
      Spacer(modifier = Modifier.height(4.dp))

      KurobaComposeThemeDependantText(
        text = errorText,
        fontSize = rememberKurobaTextUnit(fontSize = 11.sp, min = 11.sp, max = 11.sp)
      )
    }
  }
}