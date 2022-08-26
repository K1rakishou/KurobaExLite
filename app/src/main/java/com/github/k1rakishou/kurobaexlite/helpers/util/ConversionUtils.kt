package com.github.k1rakishou.kurobaexlite.helpers.util

import android.graphics.Color
import okhttp3.internal.and


object ConversionUtils {

  @JvmStatic
  fun intToByteArray(value: Int): ByteArray {
    return byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte()
    )
  }

  @JvmStatic
  fun intToCharArray(value: Int): CharArray {
    return charArrayOf(
      (value ushr 24).toChar(),
      (value ushr 16).toChar(),
      (value ushr 8).toChar(),
      value.toChar()
    )
  }

  @JvmStatic
  fun byteArrayToInt(bytes: ByteArray): Int {
    return (bytes[0] and 0xFF) shl 24 or
      ((bytes[1] and 0xFF) shl 16) or
      ((bytes[2] and 0xFF) shl 8) or
      ((bytes[3] and 0xFF) shl 0)
  }

  @JvmStatic
  fun charArrayToInt(bytes: CharArray): Int {
    return (bytes[0].code.toByte() and 0xFF) shl 24 or
      ((bytes[1].code.toByte() and 0xFF) shl 16) or
      ((bytes[2].code.toByte() and 0xFF) shl 8) or
      ((bytes[3].code.toByte() and 0xFF) shl 0)
  }

  @JvmOverloads
  @JvmStatic
  fun toIntOrNull(maybeInt: String, radix: Int = 16): Int? {
    return maybeInt.toIntOrNull(radix)
  }

  @JvmStatic
  fun colorFromArgb(alpha: Int, r: String?, g: String?, b: String?): Int? {
    val red = r?.toIntOrNull(radix = 10) ?: return null
    val green = g?.toIntOrNull(radix = 10) ?: return null
    val blue = b?.toIntOrNull(radix = 10) ?: return null

    return Color.argb(alpha, red, green, blue)
  }

}