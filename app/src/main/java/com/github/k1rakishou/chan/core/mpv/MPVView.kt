package com.github.k1rakishou.chan.core.mpv

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_FLAG
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_INT64
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_NONE
import com.github.k1rakishou.chan.core.mpv.MPVLib.mpvFormat.MPV_FORMAT_STRING
import com.github.k1rakishou.kurobaexlite.helpers.annotations.DoNotStrip
import kotlin.reflect.KProperty
import logcat.logcat

/**
 * Taken from https://github.com/mpv-android/mpv-android
 *
 * DO NOT RENAME!
 * DO NOT MOVE!
 * NATIVE LIBRARIES DEPEND ON THE CLASS PACKAGE!
 * */

@DoNotStrip
class MPVView(
    context: Context,
    attrs: AttributeSet?
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {
    private var filePath: String? = null
    private var surfaceAttached = false
    private var _initialized = false

    private lateinit var mpvSettings: MpvSettings

    fun create(mpvSettings: MpvSettings) {
        this.mpvSettings = mpvSettings

        surfaceTextureListener = this
        observeProperties()

        _initialized = true
    }

    fun destroy() {
        this.filePath = null

        // Disable surface callbacks to avoid using unintialized mpv state
        surfaceTextureListener = null

        _initialized = false
    }

    fun playFile(filePath: String) {
        if (!MPVLib.librariesAreLoaded()) {
            logcat(TAG) { "playFile() librariesAreLoaded: false" }
            return
        }

        if (!surfaceAttached) {
            this.filePath = filePath
        } else {
            this.filePath = null
            MPVLib.mpvCommand(arrayOf("loadfile", filePath))
        }

        if (mpvSettings.videoAutoLoop) {
            MPVLib.mpvSetOptionString("loop-file", "inf")
        } else {
            MPVLib.mpvSetOptionString("loop-file", "no")
        }
    }

    private fun observeProperties() {
        // This observes all properties needed by MPVView or MPVActivity
        data class Property(val name: String, val format: Int)
        val p = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("demuxer-cache-duration", MPV_FORMAT_INT64),
            Property("duration", MPV_FORMAT_INT64),
            Property("pause", MPV_FORMAT_FLAG),
            Property("audio", MPV_FORMAT_FLAG),
            Property("mute", MPV_FORMAT_STRING),
            Property("video-params", MPV_FORMAT_NONE),
            Property("video-format", MPV_FORMAT_NONE),
        )

        for ((name, format) in p) {
            MPVLib.observeProperty(name, format)
        }
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }
    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    // Property getters/setters

    var paused: Boolean?
        get() = MPVLib.mpvGetPropertyBoolean("pause")
        set(paused) = MPVLib.mpvSetPropertyBoolean("pause", paused!!)

    val duration: Int?
        get() = MPVLib.mpvGetPropertyInt("duration")

    val demuxerCacheDuration: Int?
        get() = MPVLib.mpvGetPropertyInt("demuxer-cache-duration")

    var timePos: Int?
        get() = MPVLib.mpvGetPropertyInt("time-pos")
        set(progress) = MPVLib.mpvSetPropertyInt("time-pos", progress!!)

    val hwdecActive: Boolean
        get() = (MPVLib.mpvGetPropertyString("hwdec-current") ?: "no") != "no"

    var playbackSpeed: Double?
        get() = MPVLib.mpvGetPropertyDouble("speed")
        set(speed) = MPVLib.mpvSetPropertyDouble("speed", speed!!)

    val filename: String?
        get() = MPVLib.mpvGetPropertyString("filename")

    val avsync: String?
        get() = MPVLib.mpvGetPropertyString("avsync")

    val decoderFrameDropCount: Int?
        get() = MPVLib.mpvGetPropertyInt("decoder-frame-drop-count")

    val frameDropCount: Int?
        get() = MPVLib.mpvGetPropertyInt("frame-drop-count")

    val containerFps: Double?
        get() = MPVLib.mpvGetPropertyDouble("container-fps")

    val estimatedVfFps: Double?
        get() = MPVLib.mpvGetPropertyDouble("estimated-vf-fps")

    val videoW: Int?
        get() = MPVLib.mpvGetPropertyInt("video-params/w")

    val videoH: Int?
        get() = MPVLib.mpvGetPropertyInt("video-params/h")

    val videoAspect: Double?
        get() = MPVLib.mpvGetPropertyDouble("video-params/aspect")

    val videoCodec: String?
        get() = MPVLib.mpvGetPropertyString("video-codec")

    val audioCodec: String?
        get() = MPVLib.mpvGetPropertyString("audio-codec")

    val audioSampleRate: Int?
        get() = MPVLib.mpvGetPropertyInt("audio-params/samplerate")

    val audioChannels: Int?
        get() = MPVLib.mpvGetPropertyInt("audio-params/channel-count")

    class TrackDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.mpvGetPropertyString(property.name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.mpvSetPropertyString(property.name, "no")
            else
                MPVLib.mpvSetPropertyInt(property.name, value)
        }
    }

    var vid: Int by TrackDelegate()
    var sid: Int by TrackDelegate()
    var aid: Int by TrackDelegate()

    // Commands

    fun cyclePause() = MPVLib.mpvCommand(arrayOf("cycle", "pause"))

    val isMuted: Boolean
        get() = MPVLib.mpvGetPropertyString("mute") != "no"

    fun pauseUnpause(pause: Boolean) {
        MPVLib.mpvSetPropertyBoolean("pause", pause)
    }

    fun muteUnmute(mute: Boolean) {
        if (mute) {
            MPVLib.mpvSetPropertyString("mute", "yes")
        } else {
            MPVLib.mpvSetPropertyString("mute", "no")
        }
    }

    fun enableDisableHwDec(enable: Boolean) {
        if (enable) {
            MPVLib.mpvSetPropertyString("hwdec", "mediacodec-copy")
        } else {
            MPVLib.mpvSetPropertyString("hwdec", "no")
        }
    }

    fun cycleHwdec() = MPVLib.mpvCommand(arrayOf("cycle-values", "hwdec", "mediacodec-copy", "no"))

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        logcat(TAG) { "attaching surface" }
        check(!surfaceAttached) { "Surface already attached!" }

        MPVLib.mpvAttachSurface(Surface(surfaceTexture))
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.mpvSetOptionString("force-window", "yes")

        if (filePath != null) {
            MPVLib.mpvCommand(arrayOf("loadfile", filePath as String))
            filePath = null
        }

        MPVLib.mpvSetPropertyString("vo", "gpu")
        surfaceAttached = true
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        logcat(TAG) { "detaching surface" }
        check(surfaceAttached) { "Surface is not attached!" }

        MPVLib.mpvSetPropertyString("vo", "null")
        MPVLib.mpvSetOptionString("force-window", "no")
        MPVLib.mpvDetachSurface()
        surfaceAttached = false

        return true
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        MPVLib.mpvSetPropertyString("android-surface-size", "${width}x$height")
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }

    companion object {
        private const val TAG = "MPVView"
    }
}
