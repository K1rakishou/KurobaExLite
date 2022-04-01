package com.github.k1rakishou.kurobaexlite.helpers

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.github.k1rakishou.kurobaexlite.BuildConfig
import java.io.File
import kotlin.system.exitProcess

class AndroidHelpers(
  private val application: Application
) {
  private val clipboardManager by lazy { application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

  fun getApiLevel(): Int {
    return Build.VERSION.SDK_INT
  }

  // Api 23
  fun isAndroidM(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
  }

  // Api 24
  fun isAndroidN(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
  }

  // Api 26
  fun isAndroidO(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
  }

  // Api 28
  fun isAndroidP(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
  }

  // Api 29
  fun isAndroid10(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  }

  // Api 30
  fun isAndroid11(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
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

  fun getAvailableSpaceInBytes(file: File): Long {
    val stat = StatFs(file.path)
    return stat.availableBlocksLong * stat.blockSizeLong
  }

  fun restartApp(activity: Activity) {
    with(activity) {
      val intent = packageManager.getLaunchIntentForPackage(packageName)
      finishAffinity()
      startActivity(intent)
      exitProcess(0)
    }
  }

  enum class FlavorType {
    Stable,
    Beta,
    Dev
  }

}