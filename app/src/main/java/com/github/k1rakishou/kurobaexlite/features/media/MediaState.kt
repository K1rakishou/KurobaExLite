package com.github.k1rakishou.kurobaexlite.features.media

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow

@Stable
sealed class MediaState {
  abstract val pageIndex: Int

  val supportedMedia: Boolean
    get() = this is Static || this is Video

  @Stable
  class Static(
    override val pageIndex: Int
  ) : MediaState()

  @Stable
  class Video(
    override val pageIndex: Int,
    muteByDefault: Boolean
  ) : MediaState() {
    val slideOffsetState = mutableFloatStateOf(0f)
    val blockAutoPositionUpdateState = mutableStateOf(false)

    val playerInitializedState = mutableStateOf(true)
    val videoStartedPlayingState = mutableStateOf(false)
    val hasAudioState = mutableStateOf(false)
    val isMutedState = mutableStateOf(muteByDefault)
    val isPausedState = mutableStateOf(false)
    val hardwareDecodingEnabledState = mutableStateOf(true)
    val timePositionState = mutableStateOf<Long?>(null)
    val demuxerCacheDurationState = mutableStateOf<Long?>(null)
    val durationState = mutableStateOf<Long?>(null)
    val playbackError = mutableStateOf<String?>(null)
    val videoParameters = mutableStateOf<VideoParameters?>(null)

    val muteEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.UNLIMITED)
    val playPauseEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.UNLIMITED)
    val hwDecEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.UNLIMITED)
    val seekEventFlow = MutableSharedFlow<Int>(extraBufferCapacity = Channel.UNLIMITED)
    val seekHintEventFlow = MutableSharedFlow<Int?>(extraBufferCapacity = Channel.UNLIMITED)

    fun toggleMute() {
      muteEventFlow.tryEmit(Unit)
    }

    fun togglePlayPause() {
      playPauseEventFlow.tryEmit(Unit)
    }

    fun toggleHwDec() {
      hwDecEventFlow.tryEmit(Unit)
    }

    fun updateSeekToHint(position: Int?) {
      seekHintEventFlow.tryEmit(position)
    }

    fun seekTo(position: Int) {
      seekEventFlow.tryEmit(position)
    }

    override fun toString(): String {
      return "VideoMediaState(slideOffset=${slideOffsetState.value}, isMuted=${isMutedState.value}, " +
        "isPaused=${isPausedState.value}, hardwareDecodingEnabled=${hardwareDecodingEnabledState.value}, " +
        "timePosition=${timePositionState.value}, duration=${durationState.value})"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Video

      if (pageIndex != other.pageIndex) return false

      return true
    }

    override fun hashCode(): Int {
      return pageIndex
    }

  }

  @Stable
  class Unsupported(
    override val pageIndex: Int
  ) : MediaState()

}

@Immutable
data class VideoParameters(
  val width: Int,
  val height: Int,
)