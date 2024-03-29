package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import com.github.k1rakishou.kurobaexlite.helpers.util.lerpFloat
import kotlinx.coroutines.flow.collectLatest

@Stable
class PullToRefreshState {
  val animatingRefreshState = mutableStateOf(false)
  val animatingBackState = mutableStateOf(false)

  fun stopRefreshing() {
    animatingRefreshState.value = false
    animatingBackState.value = false
  }

}

@Composable
fun rememberPullToRefreshState(): PullToRefreshState {
  return remember { PullToRefreshState() }
}

@Composable
fun PullToRefresh(
  pullToRefreshEnabled: Boolean = true,
  topPadding: Dp = 0.dp,
  circleRadius: Dp = 16.dp,
  pullThreshold: Dp = 100.dp,
  pullToRefreshState: PullToRefreshState,
  canPull: () -> Boolean = { true },
  onTriggered: () -> Unit,
  content: @Composable () -> Unit
) {
  val view = LocalView.current
  val density = LocalDensity.current
  val chanTheme = LocalChanTheme.current

  val circleRadiusPx = remember(key1 = circleRadius) { with(density) { circleRadius.toPx() } }
  val topPaddingPx = remember(key1 = topPadding) { with(density) { topPadding.toPx() } }
  val pullThresholdPx = remember(key1 = pullThreshold) { with(density) { pullThreshold.toPx() } }

  var pullToRefreshPulledPx by remember { mutableFloatStateOf(0f) }
  var isTouching by remember { mutableStateOf(false) }
  var isPulling by remember { mutableStateOf(false) }
  var refreshRotationAnimation by remember { mutableStateOf<Float?>(null) }
  var animatingRefresh by pullToRefreshState.animatingRefreshState
  var animatingBack by pullToRefreshState.animatingBackState
  val pullingBlocked by remember { derivedStateOf { animatingRefresh || animatingBack } }
  val passedPullThreshold by remember { derivedStateOf { pullToRefreshPulledPx > pullThresholdPx } }

  LaunchedEffect(
    key1 = Unit,
    block = {
      snapshotFlow { passedPullThreshold }
        .collectLatest { passed ->
          if (passed) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
          }
        }
    }
  )

  val nestedScrollConnection = remember {
    object : NestedScrollConnection {

      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (canPull() && pullToRefreshEnabled && !pullingBlocked && isTouching && isPulling && available.y < 0f) {
          pullToRefreshPulledPx = calculateNextPulledPx(
            pullToRefreshPulledPx = pullToRefreshPulledPx,
            pullThresholdPx = pullThresholdPx,
            circleRadiusPx = circleRadiusPx,
            available = available
          )

          return available.copy(x = 0f)
        }

        return super.onPreScroll(available, source)
      }

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
      ): Offset {
        if (canPull() && pullToRefreshEnabled && !pullingBlocked && isTouching && available.y != 0f) {
          isPulling = true
          pullToRefreshPulledPx = calculateNextPulledPx(
            pullToRefreshPulledPx = pullToRefreshPulledPx,
            pullThresholdPx = pullThresholdPx,
            circleRadiusPx = circleRadiusPx,
            available = available
          )

          return available.copy(x = 0f)
        }

        return Offset.Zero
      }

      override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (passedPullThreshold) {
          animatingBack = false
          animatingRefresh = true
        } else {
          animatingBack = true
          animatingRefresh = false
        }

        return super.onPostFling(consumed, available)
      }

    }
  }

  LaunchedEffect(
    key1 = animatingRefresh,
    key2 = animatingBack,
    block = {
      try {
        if (animatingRefresh) {
          onTriggered()

          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 500, easing = LinearEasing))
          ) { progress, _ -> refreshRotationAnimation = progress  }
        } else if (animatingBack) {
          val startY = pullToRefreshPulledPx
          val endY = 0f

          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = tween(durationMillis = 125, easing = LinearEasing)
          ) { progress, _ -> pullToRefreshPulledPx = lerpFloat(startY, endY, progress)  }
        }
      } finally {
        refreshRotationAnimation = null
        animatingRefresh = false
        animatingBack = false
        isPulling = false
        pullToRefreshPulledPx = 0f
      }
    })

  Box(
    modifier = Modifier
      .pointerInput(
        key1 = Unit,
        block = {
          awaitEachGesture {
            val firstEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
            if (firstEvent.type != PointerEventType.Press) {
              return@awaitEachGesture
            }

            isTouching = true

            try {
              while (true) {
                val maybeUpOrCancel = awaitPointerEvent(pass = PointerEventPass.Initial)
                if (maybeUpOrCancel.type == PointerEventType.Release) {
                  break
                }

                if (maybeUpOrCancel.changes.fastAll { it.changedToUpIgnoreConsumed() }) {
                  break
                }
              }
            } finally {
              isTouching = false
            }
          }
        }
      )
      .nestedScroll(nestedScrollConnection)
  ) {
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
      modifier = Modifier
        .onSizeChanged { size -> contentSize = size }
    ) {
      content()
    }

    val trianglePath = remember { Path() }

    if (contentSize.width > 0 && contentSize.height > 0) {
      val stroke = with(LocalDensity.current) {
        remember { Stroke(width = 2.dp.toPx(), cap = StrokeCap.Square) }
      }
      val triangleHeight = with(LocalDensity.current) { remember { 6.dp.toPx() } }
      val triangleWidth = with(LocalDensity.current) { remember { 4.dp.toPx() } }

      val widthDp = with(density) { remember(key1 = contentSize.width) { contentSize.width.toDp() } }
      val heightDp = with(density) { remember(key1 = contentSize.height) { contentSize.height.toDp() } }

      Canvas(
        modifier = Modifier.size(width = widthDp, height = heightDp),
        onDraw = {
          if (!pullToRefreshEnabled || !isPulling || pullToRefreshPulledPx <= 0f) {
            return@Canvas
          }

          val xPos = size.width / 2f
          val yPos = pullToRefreshPulledPx + topPaddingPx - circleRadiusPx
          val center = Offset(xPos, yPos)

          val cx = center.x
          val cy = center.y

          trianglePath.reset()
          trianglePath.moveTo(cx - triangleWidth, cy)   // left bottom
          trianglePath.lineTo(cx, cy - triangleHeight)  // top center
          trianglePath.lineTo(cx + triangleWidth, cy)   // right bottom
          trianglePath.moveTo(cx - triangleWidth, cy)   // left bottom
          trianglePath.close()

          val circleColor = if (passedPullThreshold) {
            chanTheme.accentColor
          } else {
            Color.White
          }

          val progressIndicatorColor = if (passedPullThreshold) {
            Color.White
          } else {
            chanTheme.accentColor
          }

          drawCircle(
            color = circleColor,
            radius = circleRadiusPx,
            center = center
          )

          val pullProgress = if (refreshRotationAnimation != null) {
            refreshRotationAnimation!!
          } else {
            (pullToRefreshPulledPx / pullThresholdPx).coerceIn(0f, 1f)
          }

          val indicatorAlpha = if (refreshRotationAnimation == null) {
            pullProgress
          } else {
            1f
          }

          withTransform(
            transformBlock = {
              rotate(
                degrees = pullProgress * 360f,
                pivot = center
              )
            },
            drawBlock = {
              drawArc(
                color = progressIndicatorColor,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                alpha = indicatorAlpha,
                topLeft = Offset(
                  center.x - (circleRadiusPx / 2f),
                  center.y - (circleRadiusPx / 2f)
                ),
                size = Size(circleRadiusPx, circleRadiusPx),
                style = stroke
              )

              withTransform(
                transformBlock = {
                  translate(top = -(circleRadiusPx / 2f))
                  rotate(90f, center)
                },
                drawBlock = {
                  drawPath(
                    path = trianglePath,
                    color = progressIndicatorColor,
                    alpha = indicatorAlpha,
                    style = Fill
                  )
                }
              )
            }
          )
        }
      )
    }
  }
}

private fun calculateNextPulledPx(
  pullToRefreshPulledPx: Float,
  pullThresholdPx: Float,
  circleRadiusPx: Float,
  available: Offset
): Float {
  return (pullToRefreshPulledPx + applyDamping(pullToRefreshPulledPx, available.y, pullThresholdPx))
    .coerceIn(0f, pullThresholdPx + (circleRadiusPx * 2f))
}

private fun applyDamping(pulled: Float, available: Float, pullThresholdPx: Float): Float {
  val damping = when (pulled) {
    in 0f..(pullThresholdPx / 3f) -> 1f
    in (pullThresholdPx / 3f)..(pullThresholdPx / 1.5f) -> 2f
    else -> 3f
  }

  return available / damping
}