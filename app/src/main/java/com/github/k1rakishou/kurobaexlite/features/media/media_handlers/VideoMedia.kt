package com.github.k1rakishou.kurobaexlite.features.media.media_handlers

import android.content.Context
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MPVView
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.ImageLoadState
import com.github.k1rakishou.kurobaexlite.features.media.MediaState
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import logcat.asLog
import logcat.logcat
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder

private const val TAG = "DisplayVideo"
private const val MPV_TAG = "mpv"
private const val MPV_OPTION_CHANGE_TOAST = "mpv_option_change_toast"

private val durationFormatter = PeriodFormatterBuilder()
  .printZeroAlways().minimumPrintedDigits(2).appendMinutes()
  .appendSeparator(":")
  .printZeroAlways().minimumPrintedDigits(2).appendSeconds()
  .toFormatter()

@Suppress("UnnecessaryVariable", "FoldInitializerAndIfToElvis")
@OptIn(ExperimentalPagerApi::class)
@Composable
fun DisplayVideo(
  isMinimized: Boolean,
  pageIndex: Int,
  pagerState: PagerState,
  toolbarHeight: Dp,
  mpvSettings: MpvSettings,
  postImageDataLoadState: ImageLoadState.Ready,
  snackbarManager: SnackbarManager,
  checkLibrariesInstalledAndLoaded: () -> Boolean,
  onPlayerLoaded: () -> Unit,
  onPlayerUnloaded: () -> Unit,
  onVideoTapped: () -> Unit,
  videoMediaState: MediaState.Video?,
  installMpvLibsFromGithubButtonClicked: () -> Unit,
  toggleMuteByDefaultState: () -> Unit
) {
  if (pagerState.currentPage != pageIndex || videoMediaState == null) {
    LaunchedEffect(key1 = Unit, block = { onPlayerUnloaded() })
    return
  }

  val librariesInstalledAndLoadedMut by produceState<Boolean?>(
    initialValue = null,
    producer = { value = checkLibrariesInstalledAndLoaded() }
  )
  val librariesInstalledAndLoaded = librariesInstalledAndLoadedMut

  if (librariesInstalledAndLoaded == null) {
    return
  }

  val context = LocalContext.current

  if (!librariesInstalledAndLoaded) {
    if (isMinimized) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        KurobaComposeIcon(
          modifier = Modifier.size(24.dp),
          drawableId = R.drawable.ic_baseline_warning_24
        )
      }
    } else {
      DisplayMpvLibrariesAreNotLoadedError(
        onPlayerLoaded = onPlayerLoaded,
        toolbarHeight = toolbarHeight,
        onVideoTapped = onVideoTapped,
        context = context,
        mpvSettings = mpvSettings,
        installMpvLibsFromGithubButtonClicked = installMpvLibsFromGithubButtonClicked
      )
    }

    return
  }

  var mpvViewMut by remember(key1 = videoMediaState) { mutableStateOf<MPVView?>(null) }
  val mpvView = mpvViewMut

  var seekingToHintMut by remember { mutableStateOf<Int?>(null) }
  val seekingToHint = seekingToHintMut

  val videoStartedPlaying by videoMediaState.videoStartedPlayingState

  val videoMediaStateSaveable = rememberSaveable(
    saver = VideoMediaStateSaveable.Saver,
    key = "pager_page_${pageIndex}"
  ) { VideoMediaStateSaveable() }

  LaunchedEffect(
    key1 = videoMediaState,
    block = {
      videoMediaState.muteEventFlow.collect {
        mpvViewMut?.let { mpvView ->
          val hasAudioState = videoMediaState.hasAudioState.value
          if (!hasAudioState || !videoStartedPlaying) {
            videoMediaState.isMutedState.value = true
            videoMediaStateSaveable.wasMuted = true
            return@let
          }

          toggleMuteByDefaultState()
          mpvView.muteUnmute(!videoMediaState.isMutedState.value)

          if (mpvView.isMuted) {
            snackbarManager.toast(
              screenKey = MainScreen.SCREEN_KEY,
              toastId = MPV_OPTION_CHANGE_TOAST,
              message = context.resources.getString(R.string.media_viewer_muted)
            )
          } else {
            snackbarManager.toast(
              screenKey = MainScreen.SCREEN_KEY,
              toastId = MPV_OPTION_CHANGE_TOAST,
              message = context.resources.getString(R.string.media_viewer_unmuted)
            )
          }

          val isMuted = mpvView.isMuted
          videoMediaState.isMutedState.value = isMuted
          videoMediaStateSaveable.wasMuted = isMuted
        }
      }
    }
  )

  LaunchedEffect(
    key1 = videoMediaState,
    block = {
      videoMediaState.playPauseEventFlow.collect {
        mpvViewMut?.let { mpvView ->
          mpvView.cyclePause()

          if (mpvView.paused == true) {
            snackbarManager.toast(
              screenKey = MainScreen.SCREEN_KEY,
              toastId = MPV_OPTION_CHANGE_TOAST,
              message = context.resources.getString(R.string.media_viewer_paused)
            )
          } else {
            snackbarManager.toast(
              screenKey = MainScreen.SCREEN_KEY,
              toastId = MPV_OPTION_CHANGE_TOAST,
              message = context.resources.getString(R.string.media_viewer_unpaused)
            )
          }
        }
      }
    }
  )

  LaunchedEffect(
    key1 = videoMediaState,
    block = {
      videoMediaState.hwDecEventFlow.collect {
        mpvViewMut?.let { mpvView ->
          val prevHwDecActive = mpvView.hwdecActive
          mpvView.cycleHwdec()
          val hwDecActive = mpvView.hwdecActive

          if (prevHwDecActive == hwDecActive) {
            snackbarManager.toast(
              screenKey = MainScreen.SCREEN_KEY,
              toastId = MPV_OPTION_CHANGE_TOAST,
              message = context.resources.getString(R.string.media_viewer_hwsw_decoding_toggle_failed)
            )
          } else {
            if (hwDecActive) {
              snackbarManager.toast(
                screenKey = MainScreen.SCREEN_KEY,
                toastId = MPV_OPTION_CHANGE_TOAST,
                message = context.resources.getString(R.string.media_viewer_switched_to_hw_decoding)
              )
            } else {
              snackbarManager.toast(
                screenKey = MainScreen.SCREEN_KEY,
                toastId = MPV_OPTION_CHANGE_TOAST,
                message = context.resources.getString(R.string.media_viewer_switched_to_sw_decoding)
              )
            }


            videoMediaStateSaveable.wasHardwareDecodingEnabled = hwDecActive
            videoMediaState.hardwareDecodingEnabledState.value = hwDecActive
          }
        }
      }
    }
  )

  LaunchedEffect(
    key1 = videoMediaState,
    block = {
      videoMediaState.seekHintEventFlow.collect { position ->
        seekingToHintMut = position
      }
    }
  )

  LaunchedEffect(
    key1 = videoMediaState,
    block = {
      videoMediaState.seekEventFlow.collect { position ->
        mpvViewMut?.timePos = position
      }
    }
  )

  val eventObserver = rememberEventObserver(
    videoMediaState = videoMediaState,
    videoMediaStateSaveable = videoMediaStateSaveable,
    mpvViewMut = mpvViewMut
  )

  val logObserver = rememberLogObserver(snackbarManager)

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .kurobaClickable(
          enabled = !isMinimized,
          hasClickIndication = false,
          onClick = { onVideoTapped() }
        ),
      factory = { viewContext ->
        val view = MPVView(viewContext, null)
        view.visibility = View.GONE

        mpvViewMut = view
        return@AndroidView view
      }
    )

    if (!videoStartedPlaying) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        KurobaComposeLoadingIndicator()
      }
    }

    if (!isMinimized) {
      SeekToHint(
        seekingToHint = seekingToHint,
        mpvView = mpvView
      )
    }
  }

  if (videoStartedPlaying) {
    LaunchedEffect(key1 = Unit, block = { onPlayerLoaded() })
  }

  if (mpvView != null) {
    DisposableEffect(
      key1 = videoMediaState,
      effect = {
        mpvView.attach(mpvSettings)
        mpvView.addObserver(eventObserver)
        MPVLib.addLogObserver(logObserver)

        videoMediaState.playerInitializedState.value = true

        mpvView.muteUnmute(videoMediaState.isMutedState.value)
        mpvView.playFile(postImageDataLoadState.fullImageUrlAsString)
        mpvView.visibility = View.VISIBLE

        return@DisposableEffect onDispose {
          videoMediaState.playerInitializedState.value = false
          videoMediaState.videoStartedPlayingState.value = false

          mpvView.detach()
          mpvView.removeObserver(eventObserver)
          MPVLib.removeLogObserver(logObserver)
          mpvViewMut = null
        }
      }
    )
  }
}

@Composable
private fun SeekToHint(
  seekingToHint: Int?,
  mpvView: MPVView?
) {
  val targetValue = if (
    seekingToHint != null &&
    mpvView != null &&
    mpvView.duration != null
  ) {
    1f
  } else {
    0f
  }

  val alphaAnimation by animateFloatAsState(
    targetValue = targetValue,
    animationSpec = tween(durationMillis = 350)
  )

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    val bgColor = remember { Color.White.copy(alpha = 0.7f) }
    val roundRect = remember { RoundedCornerShape(CornerSize(4.dp)) }

    Box(
      modifier = Modifier
        .wrapContentSize()
        .graphicsLayer { alpha = alphaAnimation }
        .background(color = bgColor, shape = roundRect),
    ) {
      val textFormatted = remember(key1 = seekingToHint, key2 = mpvView?.duration) {
        val currentPosFormatted = durationFormatter.print(Period.seconds(seekingToHint ?: 0).normalizedStandard())
        val durationFormatted = durationFormatter.print(Period.seconds(mpvView?.duration ?: 0).normalizedStandard())

        return@remember "${currentPosFormatted} / ${durationFormatted}"
      }

      Text(
        modifier = Modifier
          .wrapContentSize()
          .padding(horizontal = 24.dp, vertical = 12.dp),
        text = textFormatted,
        color = Color.Black,
        fontSize = 26.sp
      )
    }
  }
}

@Composable
private fun rememberLogObserver(
  snackbarManager: SnackbarManager
): MPVLib.LogObserver {
  return remember {
    MPVLib.LogObserver { prefix, level, text ->
      when (level) {
        MPVLib.mpvLogLevel.MPV_LOG_LEVEL_FATAL -> {
          logcatError(MPV_TAG) { "[FATAL] ${prefix} ${text}" }
          snackbarManager.toast(
            screenKey = MainScreen.SCREEN_KEY,
            message = "Mpv fatal error. ${prefix} ${text}"
          )
        }
        MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> {
          logcatError(MPV_TAG) { "[ERROR] ${prefix} ${text}" }
        }
        MPVLib.mpvLogLevel.MPV_LOG_LEVEL_INFO -> {
          logcat(MPV_TAG) { "[Info] ${prefix} ${text}" }
        }
        MPVLib.mpvLogLevel.MPV_LOG_LEVEL_DEBUG,
        MPVLib.mpvLogLevel.MPV_LOG_LEVEL_WARN -> {
          // no-op
        }
      }
    }
  }
}

@Composable
private fun rememberEventObserver(
  videoMediaState: MediaState.Video,
  videoMediaStateSaveable: VideoMediaStateSaveable,
  mpvViewMut: MPVView?,
): MPVLib.EventObserver {
  var videoStartedPlaying by videoMediaState.videoStartedPlayingState
  val mpvViewMutUpdated by rememberUpdatedState(newValue = mpvViewMut)
  val videoMediaStateUpdated by rememberUpdatedState(newValue = videoMediaState)
  val videoMediaStateSaveableUpdated by rememberUpdatedState(newValue = videoMediaStateSaveable)

  val blockAutoPositionUpdateState by videoMediaState.blockAutoPositionUpdateState
  val blockAutoPositionUpdateUpdated by rememberUpdatedState(newValue = blockAutoPositionUpdateState)

  return remember {
    object : MPVLib.EventObserver {

      private val canUpdateState: Boolean
        get() = !blockAutoPositionUpdateUpdated &&
          !videoMediaStateSaveableUpdated.needRestoreState &&
          videoStartedPlaying

      override fun eventProperty(property: String) {}
      override fun eventProperty(property: String, value: Long) {
        when (property) {
          "time-pos" -> {
            if (!canUpdateState) {
              return
            }

            videoMediaState.timePositionState.value = value
            mpvViewMut?.let { mpvView ->
              val prev = videoMediaState.demuxerCacheDurationState.value ?: 0L
              val current = mpvView.demuxerCacheDuration?.toLong() ?: 0L

              videoMediaState.demuxerCacheDurationState.value = Math.max(prev, current)
            }
            videoMediaStateSaveableUpdated.prevTimePosition = value.toInt()
            updateSliderPositionState()

            if (mpvViewMutUpdated != null) {
              if (videoMediaState.durationState.value == null) {
                mpvViewMutUpdated?.let { mpvView ->
                  mpvView.duration?.let { duration ->
                    videoMediaState.durationState.value = duration.toLong()
                  }
                }
              }

              if (videoMediaState.demuxerCacheDurationState.value == null) {
                mpvViewMutUpdated?.let { mpvView ->
                  mpvView.demuxerCacheDuration?.let { cacheDuration ->
                    videoMediaState.demuxerCacheDurationState.value = cacheDuration.toLong()
                  }
                }
              }
            }
          }
          "demuxer-cache-duration" -> {
            if (!canUpdateState) {
              return
            }

            val prev = videoMediaState.demuxerCacheDurationState.value ?: 0L
            videoMediaState.demuxerCacheDurationState.value = Math.max(prev, value)
          }
          "duration" -> {
            if (!canUpdateState) {
              return
            }

            videoMediaState.durationState.value = value
            updateSliderPositionState()
          }
        }
      }

      override fun eventProperty(property: String, value: Boolean) {
        if (videoMediaStateSaveableUpdated.needRestoreState) {
          return
        }

        when (property) {
          "pause" -> {
            videoMediaState.isPausedState.value = value
            videoMediaStateSaveableUpdated.wasPaused = value
          }
        }
      }

      override fun eventProperty(property: String, value: String) {
        if (videoMediaStateSaveableUpdated.needRestoreState) {
          return
        }

        when (property) {
          "mute" -> {
            // no-op
          }
          "hwdec" -> {
            val usingHardwareDecoding = value != "no"

            videoMediaState.hardwareDecodingEnabledState.value = usingHardwareDecoding
            videoMediaStateSaveableUpdated.wasHardwareDecodingEnabled = usingHardwareDecoding
          }
        }
      }

      override fun event(eventId: Int) {
        when (eventId) {
          MPVLib.mpvEventId.MPV_EVENT_IDLE -> {
            logcat(TAG) { "onEvent MPV_EVENT_IDLE" }
          }
          MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> {
            logcat(TAG) { "onEvent MPV_EVENT_PLAYBACK_RESTART" }

            videoStartedPlaying = true
            videoMediaState.hasAudioState.value = mpvViewMutUpdated?.audioCodec != null

            mpvViewMutUpdated?.let { mpvView ->
              if (videoMediaStateSaveableUpdated.needRestoreState) {
                videoMediaStateSaveableUpdated.wasMuted?.let { mute -> mpvView.muteUnmute(mute) }
                videoMediaStateSaveableUpdated.wasPaused?.let { pause -> mpvView.pauseUnpause(pause) }
                videoMediaStateSaveableUpdated.wasHardwareDecodingEnabled?.let { enable -> mpvView.enableDisableHwDec(enable) }
                videoMediaStateSaveableUpdated.prevTimePosition?.let { prevTime -> mpvView.timePos = prevTime }

                videoMediaStateSaveableUpdated.needRestoreState = false
              }

              videoMediaStateUpdated.timePositionState.value = mpvView.timePos?.toLong()
              videoMediaStateUpdated.durationState.value = mpvView.duration?.toLong()
              videoMediaStateUpdated.demuxerCacheDurationState.value = mpvView.demuxerCacheDuration?.toLong()
            }
          }
          MPVLib.mpvEventId.MPV_EVENT_AUDIO_RECONFIG -> {
            logcat(TAG) { "onEvent MPV_EVENT_AUDIO_RECONFIG" }
          }
          MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
            logcat(TAG) { "onEvent MPV_EVENT_FILE_LOADED" }
          }
          MPVLib.mpvEventId.MPV_EVENT_START_FILE -> {
            logcat(TAG) { "onEvent MPV_EVENT_START_FILE" }
          }
          MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
            logcat(TAG) { "onEvent MPV_EVENT_END_FILE" }
          }
          MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN -> {
            logcat(TAG) { "onEvent MPV_EVENT_SHUTDOWN" }
          }
          else -> {
            // no-op
          }
        }
      }

      private fun updateSliderPositionState() {
        val timePositionMut = videoMediaState.timePositionState.value
        val durationMut = videoMediaState.durationState.value

        val timePosition = timePositionMut
        val duration = durationMut

        if (timePosition == null || duration == null) {
          return
        }

        videoMediaState.slideOffsetState.value = (timePosition.toFloat() / duration.toFloat())
          .coerceIn(0f, 1f)
      }
    }
  }
}

@Composable
private fun DisplayMpvLibrariesAreNotLoadedError(
  onPlayerLoaded: () -> Unit,
  toolbarHeight: Dp,
  onVideoTapped: () -> Unit,
  context: Context,
  mpvSettings: MpvSettings,
  installMpvLibsFromGithubButtonClicked: () -> Unit
) {
  LaunchedEffect(key1 = Unit, block = { onPlayerLoaded() })

  val lastError = remember { MPVLib.getLastError() }
  val additionalPaddings = PaddingValues(top = toolbarHeight)

  InsetsAwareBox(
    modifier = Modifier
      .fillMaxSize()
      .kurobaClickable(hasClickIndication = false, onClick = { onVideoTapped() }),
    additionalPaddings = additionalPaddings,
    contentAlignment = Alignment.Center,
  ) {
    val errorMessage = remember {
      buildString {
        if (lastError != null) {
          appendLine("Mpv load error: ${lastError.asLog()}")
        } else {
          appendLine(context.getString(R.string.media_viewer_plugins_failed_to_load_mpv_libs))

          val status = getLibsStatus(context, mpvSettings)
          appendLine()
          appendLine(status)
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 8.dp, vertical = 16.dp),
        text = errorMessage,
        color = Color.White,
        fontSize = 16.sp
      )

      Spacer(modifier = Modifier.height(16.dp))

      KurobaComposeTextButton(
        modifier = Modifier
          .wrapContentWidth()
          .padding(horizontal = 16.dp)
          .align(Alignment.CenterHorizontally),
        text = stringResource(id = R.string.media_viewer_plugins_install_mpv_libs_from_github),
        onClick = { installMpvLibsFromGithubButtonClicked() }
      )
    }

  }
}

private fun getLibsStatus(context: Context, mpvSettings: MpvSettings): String {
  return MPVLib.getInstalledLibraries(context.applicationContext, mpvSettings.mpvNativeLibsDir)
    .entries
    .joinToString(
      separator = "\n",
      transform = { entry ->
        val libName = entry.key
        val installed = entry.value

        val res = if (installed) {
          context.resources.getString(R.string.media_viewer_plugins_libs_status_lib_installed)
        } else {
          context.resources.getString(R.string.media_viewer_plugins_libs_status_lib_missing)
        }

        return@joinToString "${libName}: ${res}"
      })
}

private class VideoMediaStateSaveable(
  var wasHardwareDecodingEnabled: Boolean? = null,
  var prevTimePosition: Int? = null,
  var wasMuted: Boolean? = null,
  var wasPaused: Boolean? = null
) {
  private val needRestoreStateInitial = wasHardwareDecodingEnabled != null ||
    prevTimePosition != null ||
    wasMuted != null ||
    wasPaused != null

  var needRestoreState: Boolean = needRestoreStateInitial

  override fun toString(): String {
    return "VideoMediaStateSaveable(wasHardwareDecodingEnabled=$wasHardwareDecodingEnabled, " +
      "prevTimePosition=$prevTimePosition, wasMuted=$wasMuted, " +
      "wasPaused=$wasPaused, needRestoreState=$needRestoreState)"
  }

  companion object {
    val Saver = listSaver<VideoMediaStateSaveable, Any?>(
      save = {
        return@listSaver listOf(
          it.wasHardwareDecodingEnabled,
          it.prevTimePosition,
          it.wasMuted,
          it.wasPaused
        )
      },
      restore = {
        return@listSaver VideoMediaStateSaveable(
          wasHardwareDecodingEnabled = it[0] as Boolean?,
          prevTimePosition = it[1] as Int?,
          wasMuted = it[2] as Boolean?,
          wasPaused = it[3] as Boolean?
        )
      }
    )
  }

}