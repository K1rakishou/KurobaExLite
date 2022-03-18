package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.unit.IntSize
import com.github.k1rakishou.kurobaexlite.helpers.Try
import java.io.InputStream
import okhttp3.internal.closeQuietly
import tachiyomi.decoder.ImageDecoder
import tachiyomi.decoder.ImageDecoder.Companion.newInstance

class TachiyomiComposeSubsamplingImageDecoder(
  private val cropBorders: Boolean = false,
  private val bitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565
) : ComposeSubsamplingImageDecoder {
  private var imageDecoder: ImageDecoder? = null

  @Synchronized
  override fun init(context: Context, inputStream: InputStream): Result<IntSize> {
    return Result.Try {
      try {
        imageDecoder = newInstance(inputStream, cropBorders)
      } finally {
        inputStream.closeQuietly()
      }

      if (imageDecoder == null) {
        throw Exception("Failed to initialize image decoder")
      }

      return@Try IntSize(
        width = imageDecoder!!.width,
        height = imageDecoder!!.height
      )
    }
  }

  @Synchronized
  override fun decodeRegion(sRect: Rect, sampleSize: Int): Result<Bitmap> {
    return Result.Try {
      val config = getImageConfig()
      val bitmap = imageDecoder!!.decode(sRect, config, sampleSize)

      return@Try bitmap
        ?: throw RuntimeException("Null region bitmap")
    }
  }

  @Synchronized
  override fun isReady(): Boolean {
    return imageDecoder?.isRecycled ?: false
  }

  @Synchronized
  override fun recycle() {
    imageDecoder?.recycle()
    imageDecoder = null
  }

  private fun getImageConfig(): Boolean {
    var rgb565 = true
    if (bitmapConfig == Bitmap.Config.ARGB_8888) {
      rgb565 = false
    }

    return rgb565
  }

}