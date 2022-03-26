package com.github.k1rakishou.kurobaexlite.helpers.decoder


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.unit.IntSize
import com.github.k1rakishou.cssi_lib.ComposeSubsamplingScaleImageDecoder
import com.github.k1rakishou.kurobaexlite.helpers.Try
import java.io.InputStream
import logcat.logcat
import tachiyomi.decoder.ImageDecoder
import tachiyomi.decoder.ImageDecoder.Companion.newInstance

class TachiyomiImageDecoder(
  private val debug: Boolean = false,
  private val cropBorders: Boolean = false,
  private val bitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565
) : ComposeSubsamplingScaleImageDecoder {
  private var imageDecoder: ImageDecoder? = null

  override fun init(context: Context, inputStream: InputStream): Result<IntSize> {
    return Result.Try {
      imageDecoder = newInstance(inputStream, cropBorders)
        ?: throw Exception("Failed to initialize image decoder")

      if (debug && ACTIVE_DECODER.add(imageDecoder!!.hashCode())) {
        logcat(tag = TAG) { "Decoder created ${imageDecoder!!.hashCode()}, decoders total: ${ACTIVE_DECODER.size}" }
      }

      return@Try IntSize(
        width = imageDecoder!!.width,
        height = imageDecoder!!.height
      )
    }
  }

  override fun decodeRegion(sRect: Rect, sampleSize: Int): Result<Bitmap> {
    return Result.Try {
      val config = getImageConfig()
      val bitmap = imageDecoder!!.decode(sRect, config, sampleSize)

      return@Try bitmap
        ?: throw RuntimeException("Null region bitmap")
    }
  }

  override fun isReady(): Boolean {
    return imageDecoder?.isRecycled ?: false
  }

  override fun recycle() {
    if (imageDecoder != null && debug && ACTIVE_DECODER.remove(imageDecoder!!.hashCode())) {
      logcat(tag = TAG) { "Decoder recycled, decoders total: ${ACTIVE_DECODER.size}" }
    }

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

  companion object {
    private const val TAG = "TachiyomiImageDecoder"
    private val ACTIVE_DECODER = hashSetOf<Int>()
  }

}