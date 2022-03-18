package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.NativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat

private const val TAG = "ComposeSubsamplingImageState"

@Composable
fun rememberComposeSubsamplingImageState(
  minTileDpiDefault: Int = 320,
  maxMaxTileSizeInfo: MaxTileSizeInfo = remember { MaxTileSizeInfo.Auto() },
  sourceDebugKey: String? = null,
  debug: Boolean = false,
  composeSubsamplingImageDecoder: () -> ComposeSubsamplingImageDecoder = remember { { TachiyomiComposeSubsamplingImageDecoder() } }
): ComposeSubsamplingImageState {
  val context = LocalContext.current

  return remember {
    ComposeSubsamplingImageState(
      context = context,
      maxMaxTileSizeInfo = maxMaxTileSizeInfo,
      composeSubsamplingImageDecoder = composeSubsamplingImageDecoder(),
      debug = debug,
      minTileDpiDefault = minTileDpiDefault,
      sourceDebugKey = sourceDebugKey
    )
  }
}

@Composable
fun ComposeSubsamplingImage(
  modifier: Modifier = Modifier,
  state: ComposeSubsamplingImageState,
  imageSource: suspend () -> ComposeSubsamplingImageSource,
  onFullImageLoaded: () -> Unit,
  onFullImageFailedToLoad: () -> Unit,
) {
  if (state.maxMaxTileSizeInfo is MaxTileSizeInfo.Auto) {
    val detected = detectCanvasMaxBitmapSize(
      onBitmapSizeDetected = { bitmapSize ->
        logcat(tag = TAG) { "CanvasMaxBitmapSize detected: ${bitmapSize}" }
        state.maxMaxTileSizeInfo.maxTileSizeState.value = bitmapSize
      }
    )

    if (!detected) {
      return
    }
  }

  val density = LocalDensity.current
  val debugValues = remember { DebugValues(density) }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .detectComposeSubsamplingImageGestures(
        onZooming = { scale -> /*TODO*/ },
        onPanning = { viewport -> /*TODO*/ }
      )
  ) {
    val minWidthPx = with(density) { remember(key1 = minWidth) { minWidth.toPx().toInt() } }
    val minHeightPx = with(density) { remember(key1 = minHeight) { minHeight.toPx().toInt() } }

    LaunchedEffect(
      key1 = minWidth,
      key2 = minHeight,
      block = {
        if (minWidthPx <= 0 || minHeightPx <= 0) {
          return@LaunchedEffect
        }

        state.availableDimensions.value = IntSize(minWidthPx, minHeightPx)
        val initializationState = state.initialize(imageSource)
        state.initializationState.value = initializationState

        when (initializationState) {
          InitializationState.Uninitialized -> {
            // no-op
          }
          is InitializationState.Error -> onFullImageFailedToLoad()
          is InitializationState.Success -> onFullImageLoaded()
        }
      }
    )

    val initializationMut by state.initializationState
    val fullImageSampleSize by state.fullImageSampleSizeState
    val scale by state.scaleState

    when (val initialization = initializationMut) {
      InitializationState.Uninitialized -> {
        // TODO(KurobaEx): show preview or loading indicator or whatever
      }
      is InitializationState.Error -> {
        val fullError = remember(key1 = initialization.exception) {
          initialization.exception.asLog()
        }

        // TODO(KurobaEx): show error composable
        InsetsAwareBox(modifier = modifier) {
          KurobaComposeText(text = fullError)
        }
      }
      is InitializationState.Success -> {
        Canvas(
          modifier = modifier,
          onDraw = {
            DrawTileGrid(
              state = state,
              scale = scale,
              fullImageSampleSize = fullImageSampleSize,
              sourceImageDimensions = state.sourceImageDimensions,
              debugValues = debugValues
            )
          }
        )
      }
    }
  }
}

@Composable
private fun detectCanvasMaxBitmapSize(onBitmapSizeDetected: (IntSize) -> Unit): Boolean {
  var maximumBitmapSizeMut by maximumBitmapSizeState
  if (maximumBitmapSizeMut == null) {
    Canvas(
      modifier = Modifier.wrapContentWidth(),
      onDraw = {
        val width = drawContext.canvas.nativeCanvas.maximumBitmapWidth
        val height = drawContext.canvas.nativeCanvas.maximumBitmapHeight

        val maxBitmapSize = IntSize(width, height)

        maximumBitmapSizeMut = maxBitmapSize
        onBitmapSizeDetected(maxBitmapSize)
      }
    )
  } else {
    LaunchedEffect(
      key1 = maximumBitmapSizeMut,
      block = {
        val maximumBitmapSize = maximumBitmapSizeMut
        if (maximumBitmapSize != null) {
          onBitmapSizeDetected(maximumBitmapSize)
        }
      })
  }

  return maximumBitmapSizeMut != null
}

private fun DrawScope.DrawTileGrid(
  state: ComposeSubsamplingImageState,
  scale: Float,
  fullImageSampleSize: Int,
  sourceImageDimensions: IntSize?,
  debugValues: DebugValues
) {
  if (sourceImageDimensions == null) {
    return
  }

  state.fitToBounds(false)

  val nativeCanvas = drawContext.canvas.nativeCanvas
  val tileMap = state.tileMap
  val bitmapPaint = state.bitmapPaint
  val bitmapMatrix = state.bitmapMatrix
  val debugTextPaint = debugValues.debugTextPaint
  val borderWidthPx = debugValues.borderWidthPx

  val sampleSize = Math.min(
    fullImageSampleSize,
    state.calculateInSampleSize(
      sourceWidth = sourceImageDimensions.width,
      sourceHeight = sourceImageDimensions.height,
      scale = scale
    )
  )

  val hasMissingTiles = state.hasMissingTiles(sampleSize)

  for ((key, value) in tileMap.entries) {
    if (key == sampleSize || hasMissingTiles) {
      for (tile in value) {
        state.sourceToViewRect(source = tile.sourceRect, target = tile.screenRect)

        if (tile.bitmap != null) {
          bitmapMatrix.reset()

          state.setMatrixArray(
            srcArray = state.srcArray,
            f0 = 0f,
            f1 = 0f,
            f2 = tile.bitmap!!.width.toFloat(),
            f3 = 0f,
            f4 = tile.bitmap!!.width.toFloat(),
            f5 = tile.bitmap!!.height.toFloat(),
            f6 = 0f,
            f7 = tile.bitmap!!.height.toFloat()
          )

          state.setMatrixArray(
            srcArray = state.dstArray,
            f0 = tile.screenRect.left.toFloat(),
            f1 = tile.screenRect.top.toFloat(),
            f2 = tile.screenRect.right.toFloat(),
            f3 = tile.screenRect.top.toFloat(),
            f4 = tile.screenRect.right.toFloat(),
            f5 = tile.screenRect.bottom.toFloat(),
            f6 = tile.screenRect.left.toFloat(),
            f7 = tile.screenRect.bottom.toFloat()
          )

          bitmapMatrix.setPolyToPoly(state.srcArray, 0, state.dstArray, 0, 4)
          nativeCanvas.drawBitmap(tile.bitmap!!, bitmapMatrix, bitmapPaint)

          if (state.debug) {
            drawRect(
              color = Color.Red.copy(alpha = 0.15f),
              topLeft = tile.screenRect.topLeft,
              size = tile.screenRect.size
            )
            drawRect(
              color = Color.Red,
              topLeft = tile.screenRect.topLeft,
              size = tile.screenRect.size,
              style = Stroke(width = borderWidthPx)
            )
          }
        }

        if (state.debug) {
          drawDebugInfo(
            tile = tile,
            nativeCanvas = nativeCanvas,
            debugTextPaint = debugTextPaint
          )
        }
      }
    }
  }

}

private fun DrawScope.drawDebugInfo(
  tile: Tile,
  nativeCanvas: NativeCanvas,
  debugTextPaint: Paint
) {
  if (tile.visible) {
    val debugText = buildString {
      append("VIS@")
      append(tile.sampleSize)
      append(" RECT (")
      append(tile.sourceRect.top)
      append(",")
      append(tile.sourceRect.left)
      append(",")
      append(tile.sourceRect.bottom)
      append(",")
      append(tile.sourceRect.right)
      append(")")
    }

    nativeCanvas.drawText(
      debugText,
      (tile.screenRect.left + (5.dp.toPx())),
      (tile.screenRect.top + (15.dp.toPx())),
      debugTextPaint
    )
  } else {
    val debugText = buildString {
      append("INV@")
      append(tile.sampleSize)
      append(" RECT (")
      append(tile.sourceRect.top)
      append(",")
      append(tile.sourceRect.left)
      append(",")
      append(tile.sourceRect.bottom)
      append(",")
      append(tile.sourceRect.right)
      append(")")
    }

    nativeCanvas.drawText(
      debugText,
      (tile.screenRect.left + (5.dp.toPx())),
      (tile.screenRect.top + (15.dp.toPx())),
      debugTextPaint
    )
  }

  if (tile.loading) {
    nativeCanvas.drawText(
      "LOADING",
      (tile.screenRect.left + (5.dp.toPx())),
      (tile.screenRect.top + (35.dp.toPx())),
      debugTextPaint
    )
  }

  if (tile.error) {
    nativeCanvas.drawText(
      "ERROR",
      (tile.screenRect.left + (5.dp.toPx())),
      (tile.screenRect.top + (55.dp.toPx())),
      debugTextPaint
    )

    drawRect(
      color = Color.Red.copy(alpha = 0.5f),
      topLeft = tile.screenRect.topLeft,
      size = tile.screenRect.size
    )
  }
}

fun Modifier.detectComposeSubsamplingImageGestures(
  onZooming: (Float) -> Unit,
  onPanning: (Rect) -> Unit
) = composed(
  inspectorInfo = {
    name = "detectComposeSubsamplingImageGestures"
    properties["onZooming"] = onZooming
    properties["onPanning"] = onPanning
  },
  factory = {
    pointerInput(
      key1 = Unit,
      block = {
        processGestures(
          onZooming,
          onPanning
        )
      }
    )
  }
)

private suspend fun PointerInputScope.processGestures(
  onZooming: (Float) -> Unit,
  onPanning: (Rect) -> Unit
) {
  val velocityTracker = VelocityTracker()

  forEachGesture {
    coroutineScope {
      val detectDoubleTapJob = launch {
        awaitPointerEventScope {
          val down = awaitFirstDown(requireUnconsumed = false)

          if (waitForUpOrCancellation() == null) {
            return@awaitPointerEventScope
          }

          velocityTracker.resetTracking()
          // TODO(KurobaEx):
        }
      }


    }
  }
}
