package com.github.k1rakishou.kurobaexlite.helpers

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.github.k1rakishou.kurobaexlite.BuildConfig

class AndroidHelpers(
  private val application: Application
) {
  private val clipboardManager by lazy { application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

  fun getApiLevel(): Int {
    return Build.VERSION.SDK_INT
  }

  fun isAndroid11(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
  }

  fun isAndroid10(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  }

  fun isAndroidO(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
  }

  fun isAndroidL_MR1(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
  }

  fun isAndroidP(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
  }

  fun isAndroidM(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
  }

  fun isAndroidN(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
  }

  fun isAndroidNMR1(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
  }

  fun setClipboardContent(label: String, content: String) {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, content))
  }

  fun getFlavorType(): FlavorType {
    return when (BuildConfig.FLAVOR_TYPE) {
      0 -> FlavorType.Stable
      1 -> FlavorType.Beta
      2 -> FlavorType.Dev
      else -> throw RuntimeException("Unknown flavor type " + BuildConfig.FLAVOR_TYPE)
    }
  }

  fun isDevFlavor(): Boolean = getFlavorType() == FlavorType.Dev

  enum class FlavorType {
    Stable,
    Beta,
    Dev
  }

}