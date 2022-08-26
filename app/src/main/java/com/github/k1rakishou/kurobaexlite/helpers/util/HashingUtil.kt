package com.github.k1rakishou.kurobaexlite.helpers.util

import android.util.Base64
import android.util.Base64InputStream
import java.io.ByteArrayOutputStream

object HashingUtil {

  fun stringBase64Decode(string: String, flags: Int = Base64.DEFAULT): String {
    return ByteArrayOutputStream().use { outputStream ->
      string.byteInputStream().use { inputStream ->
        Base64InputStream(inputStream, flags).use { base64FilterStream ->
          base64FilterStream.copyTo(outputStream)
        }
      }

      return@use outputStream.toString()
    }
  }

}