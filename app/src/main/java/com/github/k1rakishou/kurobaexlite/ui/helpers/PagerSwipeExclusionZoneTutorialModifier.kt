package com.github.k1rakishou.kurobaexlite.ui.helpers

import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

fun Modifier.drawPagerSwipeExclusionZoneTutorial(
  pagerSwipeExclusionZone: Rect,
  onTutorialFinished: () -> Unit
): Modifier {
  return composed {
    val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
    val appSettings = koinRemember<AppSettings>()
    val chanThreadManager = koinRemember<ChanThreadManager>()

    val currentlyOpenedCatalog by chanThreadManager.currentlyOpenedCatalogFlow.collectAsState()
    if (currentlyOpenedCatalog == null) {
      return@composed Modifier
    }

    val drawerDragGestureTutorialShown by appSettings.drawerDragGestureTutorialShown
      .listen()
      .collectAsState(initial = true)

    if (drawerDragGestureTutorialShown) {
      return@composed Modifier
    }

    val drawerVisibility by globalUiInfoManager.drawerVisibilityFlow.collectAsState()
    if (drawerVisibility is DrawerVisibility.Opened) {
      LaunchedEffect(
        key1 = Unit,
        block = {
          onTutorialFinished()
          appSettings.drawerDragGestureTutorialShown.write(true)
        }
      )
    }

    var delayFinished by remember { mutableStateOf(false) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        delay(2000)
        delayFinished = true
      }
    )

    if (!delayFinished) {
      return@composed Modifier
    }

    val density = LocalDensity.current
    val text = stringResource(id = R.string.pager_exclusion_zone_drag_me)
    val textSize = with(density) { 24.sp.toPx() }
    val textTopPadding = with(density) { 32.dp.toPx() }

    val arrowWidth = with(density) { 24.dp.toPx() }
    val arrowHeight = with(density) { 48.dp.toPx() }
    val arrowsTopOffset = with(density) { 80.dp.toPx() }
    val arrowStrokeWidth = with(density) { 4.dp.toPx() }
    val startOffset = with(density) { 8.dp.toPx() }
    val arrowsPadding = with(density) { 4.dp.toPx() }

    val textPaint = remember {
      TextPaint().apply {
        this.textSize = textSize
        this.color = android.graphics.Color.WHITE
        this.style = Paint.Style.FILL
        this.setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
      }
    }

    val textWidth = remember { textPaint.measureText(text) }
    val arrowsCount = 6

    var currentFocusedArrow by remember { mutableStateOf(0) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        while (isActive) {
          delay(125L)

          currentFocusedArrow = (currentFocusedArrow + 1) % arrowsCount
        }
      }
    )

    return@composed drawWithContent {
      drawContent()

      if (pagerSwipeExclusionZone.size.isEmpty()) {
        return@drawWithContent
      }

      translate(
        left = pagerSwipeExclusionZone.topLeft.x,
        top = pagerSwipeExclusionZone.topLeft.y
      ) {
        drawRect(
          color = Color.Blue,
          topLeft = Offset.Zero,
          size = pagerSwipeExclusionZone.size,
          alpha = 0.5f
        )

        val leftOffset = ((pagerSwipeExclusionZone.width - textWidth) / 2f)
        drawContext.canvas.nativeCanvas.drawText(text, leftOffset, textTopPadding, textPaint)

        translate(top = arrowsTopOffset) {
          var offset = startOffset
          val increment = arrowsPadding + arrowWidth

          repeat(arrowsCount) { arrowIndex ->
            val alpha = if (currentFocusedArrow == arrowIndex) 1f else .5f

            drawArrow(
              alpha = alpha,
              offsetX = offset,
              size = Size(width = arrowWidth, height = arrowHeight),
              strokeWidth = arrowStrokeWidth
            )

            offset += increment
          }
        }
      }
    }
  }
}

private fun DrawScope.drawArrow(alpha: Float, offsetX: Float, size: Size, strokeWidth: Float) {
  val arrowPath = Path().apply {
    moveTo(offsetX, 0f)
    lineTo(offsetX + size.width, size.height / 2f)
    lineTo(offsetX, size.height)
  }

  drawPath(
    path = arrowPath,
    color = Color.White,
    style = Stroke(width = strokeWidth),
    alpha = alpha
  )
}
