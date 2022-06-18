package com.github.k1rakishou.kurobaexlite.ui.helpers.floating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.media.MediaState
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenState
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MpvSeekbar
import com.github.k1rakishou.kurobaexlite.features.media.helpers.processTapToSeekGesture
import com.github.k1rakishou.kurobaexlite.helpers.settings.IntPositionJson
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable

abstract class MinimizableFloatingComposeScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  abstract val isScreenMinimized: MutableState<Boolean>

  override val ignoreBackPresses: Boolean
    get() {
      if (isScreenMinimized.value) {
        return true
      }

      return false
    }

  override fun onDisposed() {
    super.onDisposed()

    isScreenMinimized.value = false
  }

  fun maximize() {
    isScreenMinimized.value = false
  }

  fun minimize() {
    isScreenMinimized.value = true
  }

  @Composable
  protected fun MinimizableContent(
    mediaViewerScreenState: MediaViewerScreenState,
    onCloseMediaViewerClicked: () -> Unit,
    content: @Composable (Boolean) -> Unit
  ) {
    val density = LocalDensity.current
    val insets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current
    val isTablet = globalUiInfoManager.isTablet

    val windowSize = remember {
      val ratio = 16f / 10f
      val widthDp = if (isTablet) 240.dp else 180.dp
      val heightDp = with(density) { (widthDp.roundToPx() / ratio).toDp() }

      return@remember DpSize(
        width = widthDp,
        height = heightDp
      )
    }

    var maxSize by remember { mutableStateOf(IntSize.Zero) }
    var minimizedWindowPosition by remember { mutableStateOf(IntOffset(0, 0)) }
    var minimizedWindowPositionCoerced by remember { mutableStateOf(IntOffset(0, 0)) }
    var showUi by remember { mutableStateOf(false) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        if (minimizedWindowPosition.x != 0 || minimizedWindowPosition.y != 0) {
          return@LaunchedEffect
        }

        val lastPosition = appSettings.miniPlayerLastPosition.read()

        minimizedWindowPosition = IntOffset(
          x = lastPosition.x,
          y = lastPosition.y
        )
      }
    )

    val isMinimized by isScreenMinimized

    val currentPageIndexMut by mediaViewerScreenState.currentPageIndex
    val currentPageIndex = currentPageIndexMut
    val currentMediaState = mediaViewerScreenState.getMediaStateByIndex(currentPageIndex)

    Box(
      modifier = Modifier
        .fillMaxSize()
        .onSizeChanged { newSize -> maxSize = newSize }
    ) {
      LaunchedEffect(
        minimizedWindowPosition,
        maxSize,
        windowSize,
        insets,
        block = {
          var resultX = minimizedWindowPosition.x
          var resultY = minimizedWindowPosition.y

          val maxWidth = maxSize.width
          val maxHeight = maxSize.height

          val windowWidth = with(density) { windowSize.width.roundToPx() }
          val windowHeight = with(density) { windowSize.height.roundToPx() }

          val topInset = with(density) { insets.top.roundToPx() }
          val bottomInset = with(density) { insets.bottom.roundToPx() }

          if (resultX <= 0) {
            resultX = 0
          } else if (resultX > maxWidth - windowWidth) {
            resultX = maxWidth - windowWidth
          }

          if (resultY <= topInset) {
            resultY = topInset
          } else if (resultY > maxHeight - windowHeight - bottomInset) {
            resultY = maxHeight - windowHeight - bottomInset
          }

          minimizedWindowPositionCoerced = IntOffset(x = resultX, y = resultY)
          appSettings.miniPlayerLastPosition.write(IntPositionJson(x = resultX, y = resultY))
        }
      )

      val sizeModifier = if (isMinimized) {
        Modifier
          .size(windowSize)
          .absoluteOffset { minimizedWindowPositionCoerced }
          .drawBehind { drawRect(chanTheme.backColorSecondary) }
          .pointerInput(
            key1 = Unit,
            block = {
              detectDragGestures(
                onDrag = { _, dragAmount ->
                  minimizedWindowPosition = IntOffset(
                    x = minimizedWindowPosition.x + dragAmount.x.toInt(),
                    y = minimizedWindowPosition.y + dragAmount.y.toInt()
                  )
                }
              )
            }
          )
      } else {
        Modifier.fillMaxSize()
      }

      Box(
        modifier = sizeModifier
          .kurobaClickable(
            enabled = isMinimized,
            onClick = { showUi = !showUi }
          )
      ) {
        content(isMinimized)

        if (isMinimized) {
          MiniPlayerControls(
            showUi = showUi,
            currentMediaState = currentMediaState,
            onCloseMediaViewerClicked = onCloseMediaViewerClicked,
            goToPreviousMedia = { mediaViewerScreenState.goToPrevMedia() },
            goToNextMedia = { mediaViewerScreenState.goToNextMedia() }
          )
        }
      }
    }
  }

  @Composable
  private fun MiniPlayerControls(
    showUi: Boolean,
    currentMediaState: MediaState?,
    onCloseMediaViewerClicked: () -> Unit,
    goToPreviousMedia: () -> Unit,
    goToNextMedia: () -> Unit
  ) {
    val alphaAnimated by animateFloatAsState(targetValue = if (showUi) 1f else 0f)
    val bgColor = remember { Color.Black.copy(alpha = 0.3f) }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = alphaAnimated }
        .background(bgColor)
    ) {
      val buttonsClickable = alphaAnimated > 0.99f

      Row(
        modifier = Modifier
          .align(Alignment.TopEnd)
      ) {
        TopButtonRow(
          currentMediaState = currentMediaState,
          buttonsClickable = buttonsClickable,
          onCloseMediaViewerClicked = onCloseMediaViewerClicked
        )
      }

      MiddleButtonRow(
        buttonsClickable = buttonsClickable,
        goToPreviousMedia = goToPreviousMedia,
        currentMediaState = currentMediaState,
        goToNextMedia = goToNextMedia
      )

      if (currentMediaState is MediaState.Video) {
        val videoDuration = currentMediaState.durationState.value ?: 0
        
        if (videoDuration > 1) {
          MiniSeekbar(currentMediaState)
        }
      }
    }
  }

  @Composable
  private fun BoxScope.MiddleButtonRow(
    buttonsClickable: Boolean,
    goToPreviousMedia: () -> Unit,
    currentMediaState: MediaState?,
    goToNextMedia: () -> Unit
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.Center),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .kurobaClickable(
            bounded = false,
            enabled = buttonsClickable,
            onClick = {
              goToPreviousMedia()
            }
          ),
        drawableId = R.drawable.ic_baseline_skip_previous_24
      )

      if (currentMediaState is MediaState.Video) {
        val isPaused by currentMediaState.isPausedState

        val drawableId = if (isPaused) {
          R.drawable.ic_baseline_play_arrow_24
        } else {
          R.drawable.ic_baseline_pause_24
        }

        KurobaComposeIcon(
          modifier = Modifier
            .size(32.dp)
            .kurobaClickable(
              bounded = false,
              enabled = buttonsClickable,
              onClick = { currentMediaState.togglePlayPause() }
            ),
          drawableId = drawableId
        )
      } else {
        Spacer(modifier = Modifier.size(32.dp))
      }

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .kurobaClickable(
            bounded = false,
            enabled = buttonsClickable,
            onClick = {
              goToNextMedia()
            }
          ),
        drawableId = R.drawable.ic_baseline_skip_next_24
      )
    }
  }

  @Composable
  private fun RowScope.TopButtonRow(
    currentMediaState: MediaState?,
    buttonsClickable: Boolean,
    onCloseMediaViewerClicked: () -> Unit
  ) {
    if (currentMediaState is MediaState.Video) {
      val isMuted by currentMediaState.isMutedState

      val drawableId = if (isMuted) {
        R.drawable.ic_volume_off_white_24dp
      } else {
        R.drawable.ic_volume_up_white_24dp
      }

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .kurobaClickable(
            bounded = false,
            enabled = buttonsClickable,
            onClick = { currentMediaState.toggleMute() }
          ),
        drawableId = drawableId
      )

      Spacer(modifier = Modifier.width(8.dp))
    }

    KurobaComposeIcon(
      modifier = Modifier
        .size(32.dp)
        .kurobaClickable(
          bounded = false,
          enabled = buttonsClickable,
          onClick = { maximize() }
        ),
      drawableId = R.drawable.ic_baseline_fullscreen_24
    )

    Spacer(modifier = Modifier.weight(1f))


    KurobaComposeIcon(
      modifier = Modifier
        .size(32.dp)
        .kurobaClickable(
          bounded = false,
          enabled = buttonsClickable,
          onClick = { onCloseMediaViewerClicked() }
        ),
      drawableId = R.drawable.ic_baseline_close_24
    )
  }

  @Composable
  private fun BoxScope.MiniSeekbar(currentMediaState: MediaState.Video) {
    val chanTheme = LocalChanTheme.current

    val timePosition by currentMediaState.timePositionState
    val duration by currentMediaState.durationState
    val demuxerCacheDuration by currentMediaState.demuxerCacheDurationState

    val videoStartedPlayingState by currentMediaState.videoStartedPlayingState
    val videoStartedPlayingUpdated by rememberUpdatedState(newValue = videoStartedPlayingState)
    val videoDuration by currentMediaState.durationState
    val videoDurationUpdated by rememberUpdatedState(newValue = videoDuration)
    val slideOffset by currentMediaState.slideOffsetState
    val lastSlideOffsetUpdated by rememberUpdatedState(newValue = slideOffset)

    val demuxerCachePercents = if (
      timePosition != null &&
      duration != null &&
      demuxerCacheDuration != null
    ) {
        ((timePosition!!.toFloat() + demuxerCacheDuration!!.toFloat()) / duration!!.toFloat())
          .coerceIn(0f, 1f)
      } else {
        null
      }

    MpvSeekbar(
      modifier = Modifier
        .height(24.dp)
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 2.dp)
        .align(Alignment.BottomCenter)
        .pointerInput(
          key1 = currentMediaState,
          block = {
            processTapToSeekGesture(
              videoStartedPlaying = { videoStartedPlayingUpdated },
              videoDuration = { videoDurationUpdated },
              lastSlideOffset = { lastSlideOffsetUpdated },
              blockAutoPositionUpdateState = {
                currentMediaState.blockAutoPositionUpdateState.value = true
              },
              unBlockAutoPositionUpdateState = {
                currentMediaState.blockAutoPositionUpdateState.value = false
              },
              updateSeekHint = { newPosition -> currentMediaState.updateSeekToHint(newPosition) },
              seekTo = { newPosition -> currentMediaState.seekTo(newPosition) },
            )
          }
        ),
      enabled = true,
      trackColor = Color.White,
      thumbColorNormal = chanTheme.accentColor,
      thumbColorPressed = Color.White,
      thumbRadiusNormalDp = 8.dp,
      thumbRadiusPressedDp = 10.dp,
      slideOffsetState = currentMediaState.slideOffsetState,
      demuxerCachePercents = demuxerCachePercents,
      onValueChange = { offset -> currentMediaState.slideOffsetState.value = offset }
    )
  }

}