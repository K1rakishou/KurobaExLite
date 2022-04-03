package com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ContentAlpha
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import com.github.k1rakishou.chan.core.mpv.MpvUtils
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeSnappingSlider
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers.VideoMediaState

@Composable
internal fun MediaViewerScreenVideoControls(videoMediaState: VideoMediaState) {
  val chanTheme = LocalChanTheme.current
  val videoStartedPlaying by videoMediaState.videoStartedPlayingState

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
      val videoDurationMut by rememberUpdatedState(newValue = videoMediaState.durationState.value)
      val lastSlideOffsetMut by rememberUpdatedState(newValue = videoMediaState.slideOffsetState.value)

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

      Text(
        text = timePositionText,
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = 16.sp
      )

      Spacer(modifier = Modifier.width(10.dp))

      KurobaComposeSnappingSlider(
        modifier = Modifier
          .height(42.dp)
          .weight(1f)
          .pointerInput(
            key1 = videoMediaState,
            block = {
              forEachGesture {
                awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }

                try {
                  videoMediaState.blockAutoPositionUpdateState.value = true

                  while (true) {
                    val event =
                      awaitPointerEventScope { awaitPointerEvent(PointerEventPass.Main) }
                    if (event.changes.fastAll { !it.pressed }) {
                      break
                    }
                  }
                } finally {
                  val videoDuration = videoDurationMut
                  val lastSlideOffset = lastSlideOffsetMut

                  if (videoDuration != null) {
                    val newPosition = (videoDuration.toFloat() * lastSlideOffset).toInt()
                    videoMediaState.seekTo(newPosition)
                  }

                  videoMediaState.blockAutoPositionUpdateState.value = false
                }
              }
            }
          ),
        trackColor = androidx.compose.ui.graphics.Color.White,
        thumbColorNormal = chanTheme.accentColorCompose,
        thumbColorPressed = androidx.compose.ui.graphics.Color.White,
        thumbRadiusNormalDp = 8.dp,
        thumbRadiusPressedDp = 10.dp,
        slideOffsetState = videoMediaState.slideOffsetState,
        onValueChange = { offset -> videoMediaState.slideOffsetState.value = offset }
      )

      Spacer(modifier = Modifier.width(10.dp))

      Text(
        text = durationText,
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = 16.sp
      )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
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
          .size(42.dp)
          .kurobaClickable(bounded = false, onClick = { videoMediaState.toggleHwDec() }),
        contentAlignment = Alignment.Center
      ) {
        Text(
          modifier = Modifier
            .alpha(if (videoStartedPlaying) 1f else ContentAlpha.disabled),
          text = hwDecText,
          color = androidx.compose.ui.graphics.Color.White,
          fontSize = 16.sp,
          textAlign = TextAlign.Center
        )
      }

      IconButton(
        modifier = Modifier
          .size(42.dp),
        enabled = hasAudio && videoStartedPlaying,
        onClick = { videoMediaState.toggleMute() }
      ) {
        val drawableId = if (isMuted) {
          R.drawable.ic_volume_off_white_24dp
        } else {
          R.drawable.ic_volume_up_white_24dp
        }

        KurobaComposeIcon(drawableId = drawableId)
      }

      Spacer(modifier = Modifier.width(8.dp))

      IconButton(
        modifier = Modifier
          .size(42.dp),
        enabled = videoStartedPlaying,
        onClick = { videoMediaState.togglePlayPause() }
      ) {
        val drawableId = if (isPaused) {
          R.drawable.exo_icon_play
        } else {
          R.drawable.exo_icon_pause
        }

        KurobaComposeIcon(drawableId = drawableId)
      }
    }
  }
}