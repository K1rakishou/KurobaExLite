package com.github.k1rakishou.zoomable.internal

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.unit.IntSize
import com.github.k1rakishou.zoomable.ImageBitmapOptions
import com.github.k1rakishou.zoomable.SubSamplingImageSource
import com.github.k1rakishou.zoomable.toAndroidConfig

/** Bitmap decoder backed by Android's [BitmapRegionDecoder]. */
internal class AndroidImageRegionDecoder private constructor(
  private val imageSource: SubSamplingImageSource,
  private val imageOptions: ImageBitmapOptions,
  private val decoder: BitmapRegionDecoder,
) : ImageRegionDecoder {

  override val imageSize: IntSize = decoder.size()

  override suspend fun decodeRegion(region: BitmapRegionTile): ImageBitmap {
    val options = BitmapFactory.Options().apply {
      inSampleSize = region.sampleSize.size
      inPreferredConfig = imageOptions.config.toAndroidConfig()
    }
    val bitmap = decoder.decodeRegion(region.bounds.toAndroidRect(), options)
    checkNotNull(bitmap) {
      "BitmapRegionDecoder returned a null bitmap. Image format may not be supported: $imageSource."
    }
    return bitmap.asImageBitmap()
  }

  override fun recycle() {
    // FYI BitmapRegionDecoder's documentation says explicit recycling is not needed,
    // but that is a lie. Instrumentation tests for SubSamplingImage() on API 31 run into
    // low memory because the native state of decoders aren't cleared after each test,
    // causing Android to panic and kill all processes (including the test).
    decoder.recycle()
  }

  companion object {
    val Factory = ImageRegionDecoder.Factory { context, imageSource, imageOptions ->
      AndroidImageRegionDecoder(
        imageSource = imageSource,
        imageOptions = imageOptions,
        decoder = imageSource.decoder(context),
      )
    }
  }
}

private fun BitmapRegionDecoder.size(): IntSize {
  return IntSize(
    width = width,
    height = height,
  )
}
