package com.github.k1rakishou.kurobaexlite.helpers.image

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Taken from https://github.com/Commit451/coil-transformations/blob/master/transformations/src/main/java/com/commit451/coiltransformations/GrayscaleTransformation.kt
 * */
class GrayscaleTransformation : Transformation {

  override val cacheKey: String = GrayscaleTransformation::class.java.name

  override suspend fun transform(input: Bitmap, size: Size): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.colorFilter = COLOR_FILTER

    val output = createBitmap(input.width, input.height, input.safeConfig)
    output.applyCanvas {
      drawBitmap(input, 0f, 0f, paint)
    }

    return output
  }

  override fun equals(other: Any?) = other is GrayscaleTransformation

  override fun hashCode() = javaClass.hashCode()

  override fun toString() = "GrayscaleTransformation()"

  private companion object {
    val COLOR_FILTER = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
  }
}

val Bitmap.safeConfig: Bitmap.Config
  get() = config ?: Bitmap.Config.ARGB_8888