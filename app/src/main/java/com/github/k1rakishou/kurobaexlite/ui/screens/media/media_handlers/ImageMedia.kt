package com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.decoder.TachiyomiImageDecoder
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.ui.screens.media.ImageLoadState
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerScreenViewModel
import java.io.File

@Composable
fun DisplayFullImage(
  postImageDataLoadState: ImageLoadState.Ready,
  imageFile: File,
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

  ComposeSubsamplingScaleImage(
    modifier = Modifier.fillMaxSize(),
    state = rememberComposeSubsamplingScaleImageState(
      scrollableContainerDirection = ScrollableContainerDirection.Horizontal,
      doubleTapZoom = 2f,
      maxScale = 3f,
      maxMaxTileSize = { MaxTileSize.Auto() },
      minimumScaleType = { MinimumScaleType.ScaleTypeCenterInside },
      imageDecoderProvider = imageDecoderProvider
    ),
    imageSourceProvider = imageSourceProvider,
    eventListener = eventListener,
    onImageTapped = { onImageTapped() }
  )
}