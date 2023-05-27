package com.github.k1rakishou.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.imageLoader

/**
 * A zoomable image that can be loaded by Coil and displayed using
 * [ZoomableImage()][com.github.k1rakishou.zoomable.ZoomableImage].
 *
 * Example usages:
 *
 * ```kotlin
 * ZoomableAsyncImage(
 *   model = "https://example.com/image.jpg",
 *   contentDescription = …
 * )
 *
 * ZoomableAsyncImage(
 *   model = ImageRequest.Builder(LocalContext.current)
 *     .data("https://example.com/image.jpg")
 *     .build(),
 *   contentDescription = …
 * )
 * ```
 *
 * See [ZoomableImage()][com.github.k1rakishou.zoomable.ZoomableImage] for full documentation of parameters.
 */
@Composable
fun ZoomableAsyncImage(
  model: Any?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
  imageLoader: ImageLoader = LocalContext.current.imageLoader,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  gesturesEnabled: Boolean = true,
  onClick: ((Offset) -> Unit)? = null,
  onLongClick: ((Offset) -> Unit)? = null,
  clipToBounds: Boolean = true,
) {
  ZoomableImage(
    image = ZoomableImageSource.coil(model, imageLoader),
    contentDescription = contentDescription,
    modifier = modifier,
    state = state,
    alpha = alpha,
    colorFilter = colorFilter,
    alignment = alignment,
    contentScale = contentScale,
    gesturesEnabled = gesturesEnabled,
    onClick = onClick,
    onLongClick = onLongClick,
    clipToBounds = clipToBounds,
  )
}

/**
 * A zoomable image that can be loaded by Coil and displayed using
 * [ZoomableImage()][com.github.k1rakishou.zoomable.ZoomableImageSource].
 *
 * Example usage:
 *
 * ```kotlin
 * ZoomableImage(
 *   image = ZoomableImageSource.coil("https://example.com/image.jpg"),
 *   contentDescription = …
 * )
 *
 * ZoomableImage(
 *   image = ZoomableImageSource.coil(
 *     ImageRequest.Builder(LocalContext.current)
 *       .data("https://example.com/image.jpg")
 *       .build()
 *   ),
 *   contentDescription = …
 * )
 * ```
 */
@Composable
fun ZoomableImageSource.Companion.coil(
  model: Any?,
  imageLoader: ImageLoader = LocalContext.current.imageLoader
): ZoomableImageSource {
  return remember(model, imageLoader) {
    CoilImageSource(model, imageLoader)
  }
}
