package com.github.k1rakishou.chan.core.mpv

import android.app.ActivityManager
import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import java.io.File

class MpvSettings(
  private val appContext: Context,
  private val androidHelpers: AndroidHelpers
) {
  val hardwareDecoding = true
  val videoFastCode = false
  val videoAutoLoop = true

  private val activityManager by lazy {
    appContext.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  }

  val mpvNativeLibsDir = File(appContext.filesDir, MPV_NATIVE_LIBS_DIR_NAME)
  val mpvDiskCacheDir = File(appContext.filesDir, MPV_DISK_CACHE_DIR_NAME)
    get() {
      if (!field.exists()) {
        field.mkdirs()
      }

      return field
    }

  val demuxerCacheSizeBytes: Long
    get() {
      if (activityManager.isLowRamDevice || !androidHelpers.isAndroidO()) {
        return 32 * ONE_MEGABYTE
      }

      if (androidHelpers.isAndroidR()) {
        return 128 * ONE_MEGABYTE
      }

      if (androidHelpers.isAndroidQ()) {
        return 96 * ONE_MEGABYTE
      }

      return 64 * ONE_MEGABYTE
    }

  companion object {
    private const val ONE_MEGABYTE = 1024L * 1024L

    private const val MPV_NATIVE_LIBS_DIR_NAME = "mpv/libs"
    private const val MPV_DISK_CACHE_DIR_NAME = "mpv/cache"
  }
}