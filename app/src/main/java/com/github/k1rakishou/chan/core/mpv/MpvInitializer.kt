package com.github.k1rakishou.chan.core.mpv

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import logcat.logcat

class MpvInitializer(
  private val applicationContext: Context,
  private val mpvSettings: MpvSettings
) {
  private val TAG = "MpvInitializer"
  private var initialized = false

  fun init() {
    if (initialized) {
      return
    }

    MPVLib.tryLoadLibraries(mpvSettings)

    if (!MPVLib.librariesAreLoaded()) {
      logcat(TAG) { "create() librariesAreLoaded: false" }
      return
    }

    if (MPVLib.isCreated()) {
      return
    }

    logcat(TAG) { "create()" }

    MPVLib.mpvCreate(applicationContext)
    MPVLib.mpvInit()

    // hwdec
    val hwdec = if (mpvSettings.hardwareDecoding) {
      "mediacodec-copy"
    } else {
      "no"
    }

    logcat(TAG) { "initOptions() hwdec: $hwdec" }

    // vo: set display fps as reported by android
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val disp = wm.defaultDisplay
      val refreshRate = disp.mode.refreshRate

      logcat(TAG) { "Display ${disp.displayId} reports FPS of $refreshRate" }
      MPVLib.mpvSetOptionString("override-display-fps", refreshRate.toString())
    } else {
      logcat(TAG) {
        "Android version too old, disabling refresh rate functionality " +
          "(${Build.VERSION.SDK_INT} < ${Build.VERSION_CODES.M})"
      }
    }

    MPVLib.mpvSetOptionString("video-sync", "audio")
    MPVLib.mpvSetOptionString("interpolation", "no")

    reloadFastVideoDecodeOption(mpvSettings)

    MPVLib.mpvSetOptionString("vo", "gpu")
    MPVLib.mpvSetOptionString("gpu-context", "android")
    MPVLib.mpvSetOptionString("hwdec", hwdec)
    MPVLib.mpvSetOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
    MPVLib.mpvSetOptionString("ao", "audiotrack,opensles")

    MPVLib.mpvSetOptionString("input-default-bindings", "yes")

    val demuxerCacheSize = mpvSettings.demuxerCacheSizeBytes
    logcat(TAG) { "initOptions() mpvDemuxerCacheMaxSize: ${demuxerCacheSize.asReadableFileSize()}" }
    MPVLib.mpvSetOptionString("demuxer-max-bytes", "${demuxerCacheSize}")
    MPVLib.mpvSetOptionString("demuxer-max-back-bytes", "${demuxerCacheSize}")

    // certain options are hardcoded:
    MPVLib.mpvSetOptionString("save-position-on-quit", "no")
    MPVLib.mpvSetOptionString("force-window", "no")
    initialized = true
  }

  fun destroy() {
    if (!initialized) {
      return
    }

    if (!MPVLib.librariesAreLoaded()) {
      logcat(TAG) { "destroy() librariesAreLoaded: false" }
      initialized = false
      return
    }

    if (!MPVLib.isCreated()) {
      return
    }

    logcat(TAG) { "destroy()" }
    MPVLib.mpvDestroy()
  }

  private fun reloadFastVideoDecodeOption(mpvSettings: MpvSettings) {
    if (!MPVLib.librariesAreLoaded()) {
      logcat(TAG) { "reloadFastVideoDecodeOption() librariesAreLoaded: false" }
      return
    }

    if (mpvSettings.videoFastCode) {
      logcat(TAG) { "initOptions() videoFastCode: true" }

      MPVLib.mpvSetOptionString("vd-lavc-fast", "yes")
      MPVLib.mpvSetOptionString("vd-lavc-skiploopfilter", "nonkey")
    } else {
      logcat(TAG) { "initOptions() videoFastCode: false" }

      MPVLib.mpvSetOptionString("vd-lavc-fast", "null")
      MPVLib.mpvSetOptionString("vd-lavc-skiploopfilter", "null")
    }
  }

}