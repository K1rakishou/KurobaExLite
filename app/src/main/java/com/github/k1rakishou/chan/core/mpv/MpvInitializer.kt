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
      logcat(TAG) { "init() already initialized" }
      return
    }

    MPVLib.tryLoadLibraries(mpvSettings)

    if (!MPVLib.librariesAreLoaded()) {
      initialized = false
      logcat(TAG) { "init() librariesAreLoaded: false" }
      return
    }

    if (MPVLib.isCreated()) {
      initialized = false
      logcat(TAG) { "init() already created" }
      return
    }

    MPVLib.mpvCreate(applicationContext)
    MPVLib.mpvSetOptionString("config", "no")
    MPVLib.mpvInit()

    // hwdec
    val hwdec = if (mpvSettings.hardwareDecoding) {
      "mediacodec-copy"
    } else {
      "no"
    }

    // vo: set display fps as reported by android
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val disp = wm.defaultDisplay
      val refreshRate = disp.mode.refreshRate

      logcat(TAG) { "init() Display ${disp.displayId} reports FPS of $refreshRate" }
      MPVLib.mpvSetOptionString("override-display-fps", refreshRate.toString())
    } else {
      logcat(TAG) {
        "init() Android version too old, disabling refresh rate functionality " +
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
    val demuxerBackCacheSize = (demuxerCacheSize / 3)
    val mpvDiskCacheDir = mpvSettings.mpvDiskCacheDir

    // TODO(KurobaEx): this shit doesn't work for some reason.
    //  There is nothing being cached on the disk.
    MPVLib.mpvSetOptionString("cache", "yes")
    MPVLib.mpvSetOptionString("cache-on-disk", "yes")
    MPVLib.mpvSetOptionString("cache-dir", mpvDiskCacheDir.path)
    MPVLib.mpvSetOptionString("demuxer-seekable-cache", "yes")
    MPVLib.mpvSetOptionString("demuxer-max-bytes", "${demuxerCacheSize}")
    MPVLib.mpvSetOptionString("demuxer-max-back-bytes", "${demuxerBackCacheSize}")

    // certain options are hardcoded:
    MPVLib.mpvSetOptionString("save-position-on-quit", "no")
    MPVLib.mpvSetOptionString("force-window", "no")

    initialized = true

    logcat(TAG) {
      "init() mpv initialized, hwdec: $hwdec, " +
        "mpvDemuxerCacheMaxSize: ${demuxerCacheSize.asReadableFileSize()}, " +
        "mpvDemuxerBackCacheMaxSize: ${demuxerBackCacheSize.asReadableFileSize()}, " +
        "mpvDiskCacheDir: ${mpvDiskCacheDir.path}, " +
        "videoFastCode: ${mpvSettings.videoFastCode}"
    }
  }

  fun destroy() {
    if (!initialized) {
      logcat(TAG) { "destroy() already destroyed" }
      return
    }

    initialized = false

    if (!MPVLib.librariesAreLoaded()) {
      logcat(TAG) { "destroy() librariesAreLoaded: false" }
      return
    }

    if (!MPVLib.isCreated()) {
      logcat(TAG) { "destroy() mpv is not created" }
      return
    }

    MPVLib.mpvDestroy()
    logcat(TAG) { "destroy() mpv destroyed" }
  }

  private fun reloadFastVideoDecodeOption(mpvSettings: MpvSettings) {
    if (!MPVLib.librariesAreLoaded()) {
      logcat(TAG) { "reloadFastVideoDecodeOption() librariesAreLoaded: false" }
      return
    }

    if (mpvSettings.videoFastCode) {
      MPVLib.mpvSetOptionString("vd-lavc-fast", "yes")
      MPVLib.mpvSetOptionString("vd-lavc-skiploopfilter", "nonkey")
    } else {
      MPVLib.mpvSetOptionString("vd-lavc-fast", "null")
      MPVLib.mpvSetOptionString("vd-lavc-skiploopfilter", "null")
    }
  }

}