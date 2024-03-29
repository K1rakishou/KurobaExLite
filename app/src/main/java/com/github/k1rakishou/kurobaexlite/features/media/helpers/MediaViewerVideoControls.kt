package com.github.k1rakishou.kurobaexlite.features.media.helpers

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.core.mpv.MpvUtils
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.media.MediaState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeThemeDependantText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine

@Composable
internal fun MediaViewerScreenVideoControls(
  videoMediaState: MediaState.Video
) {
  val chanTheme = LocalChanTheme.current
  val disabledAlpha = ContentAlpha.disabled

  val videoStartedPlayingState by videoMediaState.videoStartedPlayingState
  val videoStartedPlayingUpdated by rememberUpdatedState(newValue = videoStartedPlayingState)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val timePosition by videoMediaState.timePositionState
      val duration by videoMediaState.durationState
      val demuxerCacheDuration by videoMediaState.demuxerCacheDurationState
      val videoDuration by videoMediaState.durationState
      val videoDurationUpdated by rememberUpdatedState(newValue = videoDuration)
      val slideOffset by videoMediaState.slideOffsetState
      val lastSlideOffsetUpdated by rememberUpdatedState(newValue = slideOffset)

      val demuxerCachePercents = if (timePosition != null && duration != null && demuxerCacheDuration != null) {
        ((timePosition!!.toFloat() + demuxerCacheDuration!!.toFloat()) / duration!!.toFloat()).coerceIn(0f, 1f)
      } else {
        null
      }

      val unknownTimeText = stringResource(id = R.string.media_viewer_mpv_unknown_time)

      val timePositionText = remember(key1 = timePosition) {
        if (timePosition == null) {
          unknownTimeText
        } else {
          MpvUtils.prettyTime(timePosition!!)
        }
      }

      val durationText = remember(key1 = duration) {
        if (duration == null) {
          unknownTimeText
        } else {
          MpvUtils.prettyTime(duration!!)
        }
      }

      KurobaComposeThemeDependantText(
        text = timePositionText,
        fontSize = 16.sp
      )

      Spacer(modifier = Modifier.width(10.dp))

      val seekbarColor = remember(key1 = chanTheme.backColor) {
        ThemeEngine.resolveDarkOrLightColor(chanTheme.backColor)
      }

      MpvSeekbar(
        modifier = Modifier
          .height(48.dp)
          .weight(1f)
          .pointerInput(
            key1 = videoMediaState,
            block = {
              processTapToSeekGesture(
                videoStartedPlaying = { videoStartedPlayingUpdated },
                videoDuration = { videoDurationUpdated },
                lastSlideOffset = { lastSlideOffsetUpdated },
                blockAutoPositionUpdateState = {
                  videoMediaState.blockAutoPositionUpdateState.value = true
                },
                unBlockAutoPositionUpdateState = {
                  videoMediaState.blockAutoPositionUpdateState.value = false
                },
                updateSeekHint = { newPosition -> videoMediaState.updateSeekToHint(newPosition) },
                seekTo = { newPosition -> videoMediaState.seekTo(newPosition) },
              )
            }
          ),
        enabled = videoStartedPlayingUpdated,
        trackColor = seekbarColor,
        thumbColorNormal = chanTheme.accentColor,
        thumbColorPressed = seekbarColor,
        thumbRadiusNormalDp = 8.dp,
        thumbRadiusPressedDp = 10.dp,
        slideOffsetState = videoMediaState.slideOffsetState,
        demuxerCachePercents = demuxerCachePercents,
        onValueChange = { offset -> videoMediaState.slideOffsetState.value = offset }
      )

      Spacer(modifier = Modifier.width(10.dp))

      KurobaComposeThemeDependantText(
        text = durationText,
        fontSize = 16.sp
      )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      val hasAudio by videoMediaState.hasAudioState
      val isMuted by videoMediaState.isMutedState
      val isPaused by videoMediaState.isPausedState
      val hwDecEnabled by videoMediaState.hardwareDecodingEnabledState

      val hwDecTextId = remember(key1 = hwDecEnabled) {
        if (hwDecEnabled) {
          R.string.media_viewer_mpv_hw_decoding
        } else {
          R.string.media_viewer_mpv_sw_decoding
        }
      }
      val hwDecText = stringResource(id = hwDecTextId)

      Box(
        modifier = Modifier
          .kurobaClickable(
            enabled = videoStartedPlayingUpdated,
            bounded = false,
            onClick = { videoMediaState.toggleHwDec() }
          )
          .size(48.dp),
        contentAlignment = Alignment.Center
      ) {
        val alpha = if (videoStartedPlayingUpdated) 1f else disabledAlpha

        KurobaComposeThemeDependantText(
          modifier = Modifier.graphicsLayer { this.alpha = alpha },
          text = hwDecText,
          fontSize = 16.sp,
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Box(
        modifier = Modifier
          .kurobaClickable(
            enabled = hasAudio && videoStartedPlayingUpdated,
            bounded = false,
            onClick = { videoMediaState.toggleMute() }
          )
          .size(48.dp),
        contentAlignment = Alignment.Center
      ) {
        val drawableId = if (isMuted) {
          R.drawable.ic_volume_off_white_24dp
        } else {
          R.drawable.ic_volume_up_white_24dp
        }

        KurobaComposeIcon(
          modifier = Modifier.size(32.dp),
          enabled = hasAudio && videoStartedPlayingUpdated,
          drawableId = drawableId
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Box(
        modifier = Modifier
          .kurobaClickable(
            enabled = videoStartedPlayingUpdated,
            bounded = false,
            onClick = { videoMediaState.togglePlayPause() }
          )
          .size(48.dp),
        contentAlignment = Alignment.Center
      ) {
        val drawableId = if (isPaused) {
          R.drawable.exo_icon_play
        } else {
          R.drawable.exo_icon_pause
        }

        KurobaComposeIcon(
          modifier = Modifier.size(32.dp),
          drawableId = drawableId,
          enabled = videoStartedPlayingUpdated,
        )
      }
    }
  }
}

suspend fun PointerInputScope.processTapToSeekGesture(
  videoStartedPlaying: () -> Boolean,
  videoDuration: () -> Long?,
  lastSlideOffset: () -> Float,
  blockAutoPositionUpdateState: () -> Unit,
  unBlockAutoPositionUpdateState: () -> Unit,
  updateSeekHint: (Int?) -> Unit,
  seekTo: (Int) -> Unit
) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)

    if (!videoStartedPlaying()) {
      return@awaitEachGesture
    }

    try {
      blockAutoPositionUpdateState()

      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.type != PointerEventType.Move) {
          break
        }

        updateHint(videoDuration, lastSlideOffset, updateSeekHint)
      }
    } finally {
      updateSeekHint(null)
      seek(videoDuration, lastSlideOffset, seekTo)

      unBlockAutoPositionUpdateState()
    }
  }
}

private fun seek(
  videoDuration: () -> Long?,
  lastSlideOffset: () -> Float,
  seekTo: (Int) -> Unit
) {
  val duration = videoDuration()
  if (duration != null) {
    val newPosition = (duration.toFloat() * lastSlideOffset()).toInt()
    seekTo(newPosition)
  }
}

private fun updateHint(
  videoDuration: () -> Long?,
  lastSlideOffset: () -> Float,
  updateSeekHint: (Int?) -> Unit
) {
  val duration = videoDuration()
  if (duration != null) {
    val newPosition = (duration.toFloat() * lastSlideOffset()).toInt()
    updateSeekHint(newPosition)
  }
}