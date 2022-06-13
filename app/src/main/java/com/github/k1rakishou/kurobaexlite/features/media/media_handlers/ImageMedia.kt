package com.github.k1rakishou.kurobaexlite.features.media.media_handlers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImage
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImageDecoder
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImageEventListener
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImageSource
import com.github.k1rakishou.cssi_lib.ImageDecoderProvider
import com.github.k1rakishou.cssi_lib.ImageSourceProvider
import com.github.k1rakishou.cssi_lib.MaxTileSize
import com.github.k1rakishou.cssi_lib.MinimumScaleType
import com.github.k1rakishou.cssi_lib.ScrollableContainerDirection
import com.github.k1rakishou.cssi_lib.rememberComposeSubsamplingScaleImageState
import com.github.k1rakishou.kurobaexlite.features.media.ImageLoadState
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.decoder.TachiyomiImageDecoder
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import java.io.File

@Composable
fun DisplayFullImage(
  isMinimized: Boolean,
  availableSize: IntSize,
  postImageDataLoadState: ImageLoadState.Ready,
  imageFile: File,
  setIsDragGestureAllowedFunc: ((currPosition: Offset, startPosition: Offset) -> Boolean) -> Unit,
  onFullImageLoaded: () -> Unit,
  onFullImageFailedToLoad: () -> Unit,
  onImageTapped: () -> Unit,
  reloadImage: () -> Unit
) {
  val eventListener = object : ComposeSubsamplingScaleImageEventListener() {
    override fun onFailedToProvideSource(error: Throwable) {
      super.onFailedToProvideSource(error)

      reloadImage()
    }

    override fun onFailedToDecodeImageInfo(error: Throwable) {
      val url = postImageDataLoadState.fullImageUrlAsString
      logcatError { "onFailedToDecodeImageInfo() url=$url, error=${error.errorMessageOrClassName()}" }

      onFullImageFailedToLoad()
    }

    override fun onFailedToLoadFullImage(error: Throwable) {
      val url = postImageDataLoadState.fullImageUrlAsString
      logcatError { "onFailedToLoadFullImage() url=$url, error=${error.errorMessageOrClassName()}" }

      onFullImageFailedToLoad()
    }

    override fun onFullImageLoaded() {
      onFullImageLoaded()
    }
  }

  val imageSourceProvider = remember(key1 = imageFile) {
    object : ImageSourceProvider {
      override suspend fun provide(): Result<ComposeSubsamplingScaleImageSource> {
        return Result.Try {
          val inputStream = try {
            imageFile.inputStream()
          } catch (error: Throwable) {
            throw MediaViewerScreenViewModel.ImageLoadException(
              postImageDataLoadState.fullImageUrl,
              "Failed to open input stream of file: ${imageFile.name}, " +
                "exists: ${imageFile.exists()}, " +
                "length: ${imageFile.length()}, " +
                "canRead: ${imageFile.canRead()}"
            )
          }

          return@Try ComposeSubsamplingScaleImageSource(
            debugKey = postImageDataLoadState.fullImageUrlAsString,
            inputStream = inputStream
          )
        }
      }
    }
  }

  val imageDecoderProvider = remember {
    object : ImageDecoderProvider {
      override suspend fun provide(): ComposeSubsamplingScaleImageDecoder {
        return TachiyomiImageDecoder()
      }
    }
  }

  val maxScale = remember(key1 = postImageDataLoadState.postImage, key2 = availableSize) {
    val postImage = postImageDataLoadState.postImage

    var scale = Math.min(
      availableSize.width.toFloat() / postImage.width.toFloat(),
      availableSize.height.toFloat() / postImage.height.toFloat()
    )

    if (scale < 2f) {
      scale += 2f
    }

    return@remember scale
  }

  val state = rememberComposeSubsamplingScaleImageState(
    maxScale,
    scrollableContainerDirection = ScrollableContainerDirection.Horizontal,
    doubleTapZoom = 2f,
    maxScale = maxScale,
    maxMaxTileSize = { MaxTileSize.Auto() },
    minimumScaleType = { MinimumScaleType.ScaleTypeCustom },
    imageDecoderProvider = imageDecoderProvider
  )
  val stateUpdated by rememberUpdatedState(newValue = state)

  LaunchedEffect(
    key1 = Unit,
    block = {
      val isDragGestureAllowed: (Offset, Offset) -> Boolean = func@ { currPosition, startPosition ->
        val panInfo = stateUpdated.getPanInfo()
        if (panInfo == null) {
          return@func false
        }

        if (currPosition.y - startPosition.y > 0 && !panInfo.touchesTop()) {
          return@func false
        } else if (currPosition.y - startPosition.y < 0 && !panInfo.touchesBottom()) {
          return@func false
        }

        return@func true
      }

      setIsDragGestureAllowedFunc(isDragGestureAllowed)
    }
  )

  val onImageTappedFunc = remember(key1 = isMinimized) {
    if (isMinimized) {
      null
    } else {
      { _: Offset -> onImageTapped() }
    }
  }

  ComposeSubsamplingScaleImage(
    modifier = Modifier.fillMaxSize(),
    state = state,
    imageSourceProvider = imageSourceProvider,
    enableGestures = !isMinimized,
    eventListener = eventListener,
    onImageTapped = onImageTappedFunc
  )
}