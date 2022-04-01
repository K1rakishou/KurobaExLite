package com.github.k1rakishou.kurobaexlite.ui.screens.media.media_handlers

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.media.ImageLoadState
import logcat.asLog
import logcat.logcat

private const val TAG = "DisplayVideo"
private const val MPV_TAG = "mpv"

@Suppress("UnnecessaryVariable", "FoldInitializerAndIfToElvis")
@OptIn(ExperimentalPagerApi::class)
@Composable
fun DisplayVideo(
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
  showVideoControls: () -> Unit,
  hideVideoControls: () -> Unit,
  installMpvLibsFromGithubButtonClicked: () -> Unit
) {
  if (pagerState.currentPage != pageIndex) {
    SideEffect { onPlayerUnloaded() }
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
    DisplayMpvLibrariesAreNotLoadedError(
      onPlayerLoaded = onPlayerLoaded,
      toolbarHeight = toolbarHeight,
      onVideoTapped = onVideoTapped,
      context = context,
      mpvSettings = mpvSettings,
      installMpvLibsFromGithubButtonClicked = installMpvLibsFromGithubButtonClicked
    )

    return
  }

  var mpvViewMut by remember { mutableStateOf<MPVView?>(null) }
  var videoStartedPlaying by remember { mutableStateOf(false) }

  val eventObserver = remember {
    object : MPVLib.EventObserver {
      override fun eventProperty(property: String) {}
      override fun eventProperty(property: String, value: Long) {}
      override fun eventProperty(property: String, value: Boolean) {}
      override fun eventProperty(property: String, value: String) {}

      override fun event(eventId: Int) {
        when (eventId) {
          MPVLib.mpvEventId.MPV_EVENT_IDLE -> {
            logcat(TAG) { "onEvent MPV_EVENT_IDLE" }
          }
          MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> {
            logcat(TAG) { "onEvent MPV_EVENT_PLAYBACK_RESTART" }
            videoStartedPlaying = true
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
          MPVLib.mpvEventId.MPV_EVENT_NONE -> { logcat(TAG) { "onEvent MPV_EVENT_NONE" } }
          MPVLib.mpvEventId.MPV_EVENT_LOG_MESSAGE -> { logcat(TAG) { "onEvent MPV_EVENT_LOG_MESSAGE" } }
          MPVLib.mpvEventId.MPV_EVENT_GET_PROPERTY_REPLY -> { logcat(TAG) { "onEvent MPV_EVENT_GET_PROPERTY_REPLY" } }
          MPVLib.mpvEventId.MPV_EVENT_SET_PROPERTY_REPLY -> { logcat(TAG) { "onEvent MPV_EVENT_SET_PROPERTY_REPLY" } }
          MPVLib.mpvEventId.MPV_EVENT_COMMAND_REPLY -> { logcat(TAG) { "onEvent MPV_EVENT_COMMAND_REPLY" } }
          MPVLib.mpvEventId.MPV_EVENT_TICK -> { logcat(TAG) { "onEvent MPV_EVENT_TICK" } }
          MPVLib.mpvEventId.MPV_EVENT_CLIENT_MESSAGE -> { logcat(TAG) { "onEvent MPV_EVENT_CLIENT_MESSAGE" } }
          MPVLib.mpvEventId.MPV_EVENT_VIDEO_RECONFIG -> { logcat(TAG) { "onEvent MPV_EVENT_VIDEO_RECONFIG" } }
          MPVLib.mpvEventId.MPV_EVENT_SEEK -> { logcat(TAG) { "onEvent MPV_EVENT_SEEK" } }
          MPVLib.mpvEventId.MPV_EVENT_PROPERTY_CHANGE -> { logcat(TAG) { "onEvent MPV_EVENT_PROPERTY_CHANGE" } }
          MPVLib.mpvEventId.MPV_EVENT_QUEUE_OVERFLOW -> { logcat(TAG) { "onEvent MPV_EVENT_QUEUE_OVERFLOW" } }
          MPVLib.mpvEventId.MPV_EVENT_HOOK -> { logcat(TAG) { "onEvent MPV_EVENT_HOOK" } }
          else -> {
            // no-op
          }
        }
      }
    }
  }

  val logObserver = remember {
    MPVLib.LogObserver { prefix, level, text ->
      when (level) {
        MPVLib.mpvLogLevel.MPV_LOG_LEVEL_FATAL -> {
          logcatError(MPV_TAG) { "[FATAL] ${prefix} ${text}" }
          snackbarManager.toast("Mpv fatal error. ${prefix} ${text}")
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

  AndroidView(
    modifier = Modifier
      .fillMaxSize()
      .kurobaClickable(
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

  val mpvView = mpvViewMut
  if (mpvView != null) {
    if (videoStartedPlaying) {
      LaunchedEffect(key1 = Unit, block = { onPlayerLoaded() })
    }

    DisposableEffect(
      key1 = mpvView,
      effect = {
        mpvView.create(context.applicationContext, mpvSettings)
        mpvView.addObserver(eventObserver)
        MPVLib.addLogObserver(logObserver)

        showVideoControls()

        mpvView.playFile(postImageDataLoadState.fullImageUrlAsString)
        mpvView.visibility = View.VISIBLE

        return@DisposableEffect onDispose {
          mpvView.destroy()
          mpvView.removeObserver(eventObserver)
          MPVLib.removeLogObserver(logObserver)

          hideVideoControls()
        }
      }
    )
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
  SideEffect { onPlayerLoaded() }

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