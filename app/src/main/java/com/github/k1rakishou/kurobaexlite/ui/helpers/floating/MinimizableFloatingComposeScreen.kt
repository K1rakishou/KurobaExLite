package com.github.k1rakishou.kurobaexlite.ui.helpers.floating

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.github.k1rakishou.kurobaexlite.helpers.settings.IntPositionJson
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

abstract class MinimizableFloatingComposeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : FloatingComposeScreen(componentActivity, navigationRouter) {
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

  // TODO(KurobaEx): store/restore last video position when minimizing/maximizing the player
  // TODO(KurobaEx): togglePlayPause/isCurrentlyPaused
  @Composable
  protected fun MinimizableContent(
    onCloseMediaViewerClicked: () -> Unit,
    goToPreviousMedia: () -> Unit,
    goToNextMedia: () -> Unit,
    togglePlayPause: () -> Unit,
    isCurrentlyPaused: () -> Boolean?,
    content: @Composable (Boolean) -> Unit
  ) {
    val density = LocalDensity.current
    val insets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current

    val windowSize = remember { DpSize(width = 160.dp, 112.dp) }
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

    val clicksFlow = remember {
      MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
      )
    }

    val isMinimized by isScreenMinimized

    LaunchedEffect(
      key1 = Unit,
      block = {
        clicksFlow
          .debounce(3000.milliseconds)
          .collectLatest {
            showUi = false
          }
      })

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
            onClick = {
              showUi = true
              clicksFlow.tryEmit(Unit)
            }
          )
      ) {
        content(isMinimized)

        if (isMinimized) {
          MiniPlayerControls(
            showUi = showUi,
            onCloseMediaViewerClicked = onCloseMediaViewerClicked,
            goToPreviousMedia = goToPreviousMedia,
            clicksFlow = clicksFlow,
            goToNextMedia = goToNextMedia
          )
        }
      }
    }
  }

  @Composable
  private fun MiniPlayerControls(
    showUi: Boolean,
    onCloseMediaViewerClicked: () -> Unit,
    goToPreviousMedia: () -> Unit,
    clicksFlow: MutableSharedFlow<Unit>,
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
        KurobaComposeIcon(
          modifier = Modifier
            .size(24.dp)
            .kurobaClickable(
              enabled = buttonsClickable,
              onClick = { maximize() }
            ),
          drawableId = R.drawable.ic_baseline_fullscreen_24
        )

        Spacer(modifier = Modifier.width(6.dp))

        KurobaComposeIcon(
          modifier = Modifier
            .size(24.dp)
            .kurobaClickable(
              enabled = buttonsClickable,
              onClick = { onCloseMediaViewerClicked() }
            ),
          drawableId = R.drawable.ic_baseline_close_24
        )
      }

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
              enabled = buttonsClickable,
              onClick = {
                goToPreviousMedia()
                clicksFlow.tryEmit(Unit)
              }
            ),
          drawableId = R.drawable.ic_baseline_skip_previous_24
        )

        KurobaComposeIcon(
          modifier = Modifier
            .size(32.dp)
            .kurobaClickable(
              enabled = buttonsClickable,
              onClick = {
                /*TODO*/
                clicksFlow.tryEmit(Unit)
              }
            ),
          drawableId = R.drawable.ic_baseline_pause_24
        )

        KurobaComposeIcon(
          modifier = Modifier
            .size(32.dp)
            .kurobaClickable(
              enabled = buttonsClickable,
              onClick = {
                goToNextMedia()
                clicksFlow.tryEmit(Unit)
              }
            ),
          drawableId = R.drawable.ic_baseline_skip_next_24
        )
      }
    }
  }

}