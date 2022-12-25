package com.github.k1rakishou.kurobaexlite.helpers.image

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation

class ColorReplacementTransformation(
  private val replacements: List<ColorReplacement>
) : Transformation {

  override val cacheKey: String = javaClass.name

  override suspend fun transform(input: Bitmap, size: Size): Bitmap {
    val allPixels = IntArray(input.height * input.width)
    input.getPixels(allPixels, 0, input.width, 0, 0, input.width, input.height)

    for (i in allPixels.indices) {
      for (colorReplacement in replacements) {
        if (colorReplacement.originalColor == allPixels[i]) {
          allPixels[i] = colorReplacement.replacementColor
          break
        }
      }
    }

    val output = createBitmap(input.width, input.height, input.safeConfig)
    output.setPixels(allPixels, 0, input.width, 0, 0, input.width, input.height)
    return output
  }

}

data class ColorReplacement(
  val originalColor: Int,
  val replacementColor: Int
)