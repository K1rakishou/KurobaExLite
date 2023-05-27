package com.github.k1rakishou.kurobaexlite.features.media.media_handlers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.features.media.ImageLoadState
import com.github.k1rakishou.zoomable.ZoomSpec
import com.github.k1rakishou.zoomable.ZoomableAsyncImage
import com.github.k1rakishou.zoomable.rememberZoomableImageState
import com.github.k1rakishou.zoomable.rememberZoomableState
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

@Composable
fun DisplayFullImage(
  availableSize: IntSize,
  postImageDataLoadState: ImageLoadState.Ready,
  setIsDragGestureAllowedFunc: ((currPosition: Offset, startPosition: Offset) -> Boolean) -> Unit,
  onFullImageLoaded: () -> Unit,
  onFullImageFailedToLoad: () -> Unit,
  onImageTapped: () -> Unit,
  reloadImage: () -> Unit
) {
  val imageLoader = LocalContext.current.imageLoader
  val context = LocalContext.current

  val maxZoomFactor = remember(key1 = postImageDataLoadState.postImage, key2 = availableSize) {
    val postImage = postImageDataLoadState.postImage
    val maxZoomFactor = Math.max(
      postImage.width.toFloat() / availableSize.width.toFloat(),
      postImage.height.toFloat()/ availableSize.height.toFloat()
    )

    return@remember maxZoomFactor.coerceIn(3f, 8f)
  }

  val zoomSpec = remember { ZoomSpec(maxZoomFactor = maxZoomFactor) }
  val zoomableState = rememberZoomableState(zoomSpec = zoomSpec)
  val zoomableImageState = rememberZoomableImageState(zoomableState)

  val imageFile = checkNotNull(postImageDataLoadState.imageFile) { "imageFile is null" }
  val imageFilePath = checkNotNull(postImageDataLoadState.imageFilePath) { "imageFilePath is null" }

  val currentOnFullImageLoaded = rememberUpdatedState(newValue = onFullImageLoaded)
  val currentOnFullImageFailedToLoad = rememberUpdatedState(newValue = onFullImageFailedToLoad)

  val coroutineScope = rememberCoroutineScope()

  val model = remember(key1 = imageFilePath) {
    ImageRequest.Builder(context)
      .data(imageFile)
      .listener(
        onSuccess = { _, _ ->
          coroutineScope.launch {
            awaitFrame()
            currentOnFullImageLoaded.value()
          }
        },
        onError = { _, _ ->
          currentOnFullImageFailedToLoad.value()
        }
      )
      .memoryCachePolicy(CachePolicy.DISABLED)
      .build()
  }

  ZoomableAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = model,
    state = zoomableImageState,
    imageLoader = imageLoader,
    onClick = { onImageTapped() },
    contentDescription = null
  )
}