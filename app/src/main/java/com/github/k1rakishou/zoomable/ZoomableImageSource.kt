package com.github.k1rakishou.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * An image that can be displayed using [ZoomableImage()][com.github.k1rakishou.zoomable.ZoomableImageSource].
 *
 * Keep in mind that this shouldn't be used directly. It is designed to provide an
 * abstraction over your favorite image loading library. More documentation can be found
 * on the [project website](https://saket.github.io/telephoto/zoomableimage/).
 */
@Stable
interface ZoomableImageSource {
  companion object; // For extensions.

  @Composable
  fun resolve(canvasSize: Flow<Size>): ResolveResult

  @Immutable
  data class ResolveResult(
    val delegate: ImageDelegate?,
    val crossfadeDuration: Duration = Duration.ZERO,
    val placeholder: Painter? = null,
  )

  @Stable
  sealed interface ImageDelegate

  /**
   * Images that will fit into memory and do not require sub-sampling. This will mostly
   * be used for GIFs, SVGs or even bitmaps that can't be represented using [SubSamplingImageSource].
   */
  @JvmInline
  @Immutable
  value class PainterDelegate(
    val painter: Painter?
  ) : ImageDelegate

  /** Bitmaps that may not fit into memory and should be sub-sampled. */
  @Immutable
  data class SubSamplingDelegate(
    val source: SubSamplingImageSource,
    val imageOptions: ImageBitmapOptions = ImageBitmapOptions.Default,
  ) : ImageDelegate
}
