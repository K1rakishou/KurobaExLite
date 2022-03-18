package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import android.content.Context
import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.DisplayMetrics
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize
import com.github.k1rakishou.kurobaexlite.helpers.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat

internal val maximumBitmapSizeState = mutableStateOf<IntSize?>(null)
private val decoderDispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
  .asCoroutineDispatcher()

class ComposeSubsamplingImageState(
  val context: Context,
  val maxMaxTileSizeInfo: MaxTileSizeInfo,
  val composeSubsamplingImageDecoder: ComposeSubsamplingImageDecoder,
  val debug: Boolean,
  private val minTileDpiDefault: Int,
  private val sourceDebugKey: String?
) : RememberObserver {
  internal val screenTranslate = PointState()
  internal val minTileDpiState by lazy { minTileDpi() }
  internal val tileMap = LinkedHashMap<Int, MutableList<Tile>>()

  private var satTemp = ScaleAndTranslate()
  private var needInitScreenTranslate = true

  var sourceImageDimensions: IntSize? = null
    private set

  val bitmapPaint by lazy {
    Paint().apply {
      isAntiAlias = true
      isFilterBitmap = true
      isDither = true
    }
  }
  val bitmapMatrix by lazy { Matrix() }
  val srcArray = FloatArray(8)
  val dstArray = FloatArray(8)

  val initializationState = mutableStateOf<InitializationState>(InitializationState.Uninitialized)
  val scaleState = mutableStateOf(1f)
  val fullImageSampleSizeState = mutableStateOf(0)
  val availableDimensions = mutableStateOf(IntSize.Zero)

  val availableWidth: Int
    get() = availableDimensions.value.width
  val availableHeight: Int
    get() = availableDimensions.value.height

  val sourceWidth: Int
    get() = requireNotNull(sourceImageDimensions?.width) { "sourceImageDimensions is null!" }
  val sourceHeight: Int
    get() = requireNotNull(sourceImageDimensions?.height) { "sourceImageDimensions is null!" }

  override fun onRemembered() {
  }

  override fun onForgotten() {
    reset()
  }

  override fun onAbandoned() {
    reset()
  }

  private fun minTileDpi(): Int {
    val metrics = context.resources.displayMetrics
    val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
    return Math.min(averageDpi, minTileDpiDefault.toFloat()).toInt()
  }

  private fun reset() {
    screenTranslate.reset()

    tileMap.entries.forEach { (_, tiles) -> tiles.forEach { tile -> tile.recycle() } }
    tileMap.clear()

    satTemp.reset()
    bitmapMatrix.reset()
    sourceImageDimensions = null
    scaleState.value = 1f
    fullImageSampleSizeState.value = 0
    availableDimensions.value = IntSize.Zero
    srcArray.fill(0f)
    dstArray.fill(0f)

    composeSubsamplingImageDecoder.recycle()

    needInitScreenTranslate = true
    initializationState.value = InitializationState.Uninitialized
  }

  suspend fun initialize(
    imageSource: suspend () -> ComposeSubsamplingImageSource
  ): InitializationState {
    BackgroundUtils.ensureMainThread()

    val imageDimensionsInfoResult = withContext(decoderDispatcher) {
      decodeImageDimensions(this@ComposeSubsamplingImageState, imageSource())
    }

    val imageDimensions = if (imageDimensionsInfoResult.isFailure) {
      val error = imageDimensionsInfoResult.exceptionOrThrow()
      logcat(tag = TAG) {
        "initialize() decodeImageDimensions() Failure!\n" +
          "sourceDebugKey=${sourceDebugKey}\n" +
          "imageDimensionsInfoResultError=${error.asLog()}"
      }

      reset()
      return InitializationState.Error(error)
    } else {
      imageDimensionsInfoResult.getOrThrow()
    }

    sourceImageDimensions = imageDimensions

    if (debug) {
      logcat(tag = TAG) { "initialize() decodeImageDimensions() Success! imageDimensions=$imageDimensions" }
    }

    satTemp.reset()
    fitToBounds(true, satTemp)

    fullImageSampleSizeState.value = calculateInSampleSize(
      sourceWidth = imageDimensions.width,
      sourceHeight = imageDimensions.height,
      scale = satTemp.scale
    )

    if (fullImageSampleSizeState.value > 1) {
      fullImageSampleSizeState.value /= 2
    }

    if (debug) {
      logcat(tag = TAG) { "initialize() fullImageSampleSizeState=${fullImageSampleSizeState.value}" }
      logcat { "initialiseTileMap maxTileDimensions=${maxMaxTileSizeInfo.width}x${maxMaxTileSizeInfo.height}" }
    }

    initialiseTileMap(
      sourceWidth = imageDimensions.width,
      sourceHeight = imageDimensions.height,
      maxTileWidth = maxMaxTileSizeInfo.width,
      maxTileHeight = maxMaxTileSizeInfo.height,
      availableWidth = availableWidth,
      availableHeight = availableHeight,
      fullImageSampleSize = fullImageSampleSizeState.value
    )

    if (debug) {
      tileMap.entries.forEach { (sampleSize, tiles) ->
        logcat { "initialiseTileMap sampleSize=$sampleSize, tilesCount=${tiles.size}" }
      }
    }

    val baseGrid = tileMap[fullImageSampleSizeState.value]!!
    baseGrid.forEach { baseTile -> baseTile.loading = true }

    loadTiles(baseGrid)

    refreshRequiredTiles(
      load = true,
      sourceWidth = imageDimensions.width,
      sourceHeight = imageDimensions.height,
      fullImageSampleSize = fullImageSampleSizeState.value,
      scale = scaleState.value
    )

    return InitializationState.Success
  }

  fun hasMissingTiles(sampleSize: Int): Boolean {
    var hasMissingTiles = false

    for ((key, value) in tileMap.entries) {
      if (key == sampleSize) {
        for (tile in value) {
          if (tile.visible && (tile.loading || tile.bitmap == null)) {
            hasMissingTiles = true
          }
        }
      }
    }

    return hasMissingTiles
  }

  private fun decodeImageDimensions(
    state: ComposeSubsamplingImageState,
    imageSource: ComposeSubsamplingImageSource
  ): Result<IntSize> {
    BackgroundUtils.ensureBackgroundThread()

    return Result.Try {
      return@Try imageSource.useInputStream { inputStream ->
        state.composeSubsamplingImageDecoder.init(state.context, inputStream).unwrap()
      }
    }
  }

  private suspend fun loadTiles(baseGrid: List<Tile>) {
    if (baseGrid.isEmpty()) {
      return
    }

    // TODO(KurobaEx): benchmark to decide how many threads should be used here
    coroutineScope {
      baseGrid.map { tile ->
        async(decoderDispatcher) {
          tile.error = false

          try {
            val decodedTileBitmap = composeSubsamplingImageDecoder.decodeRegion(
              sRect = tile.fileSourceRect.toAndroidRect(),
              sampleSize = tile.sampleSize
            ).unwrap()

            tile.bitmap = decodedTileBitmap
            tile.error = false
          } catch (error: Throwable) {
            if (debug) {
              logcatError { "Failed to decode tile: $tile, error: ${error.asLog()}" }
            }

            tile.error = true
          } finally {
            tile.loading = false
          }
        }
        // TODO(KurobaEx): maybe we don't need to await them all here and can just draw them
        //  asyncronously once they load?
      }.awaitAll()
    }
  }

  private suspend fun refreshRequiredTiles(
    load: Boolean,
    sourceWidth: Int,
    sourceHeight: Int,
    fullImageSampleSize: Int,
    scale: Float
  ) {
    val sampleSize = Math.min(
      fullImageSampleSize,
      calculateInSampleSize(sourceWidth, sourceHeight, scale)
    )

    val tilesToLoad = mutableListOf<Tile>()

    // Load tiles of the correct sample size that are on screen. Discard tiles off screen, and those that are higher
    // resolution than required, or lower res than required but not the base layer, so the base layer is always present.
    for ((_, value) in tileMap) {
      for (tile in value) {
        if (tile.sampleSize < sampleSize || tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize) {
          tile.visible = false
          tile.recycle()
        }

        if (tile.sampleSize == sampleSize) {
          if (tileVisible(tile)) {
            tile.visible = true

            if (!tile.loading && tile.bitmap == null && load) {
              tilesToLoad += tile
            }
          } else if (tile.sampleSize != fullImageSampleSize) {
            tile.visible = false
            tile.recycle()
          }
        } else if (tile.sampleSize == fullImageSampleSize) {
          tile.visible = true
        }
      }
    }

    loadTiles(tilesToLoad)
  }

  private fun tileVisible(tile: Tile): Boolean {
    val sVisLeft = viewToSourceX(0f)
    val sVisRight = viewToSourceX(availableWidth.toFloat())
    val sVisTop = viewToSourceY(0f)
    val sVisBottom = viewToSourceY(availableHeight.toFloat())

    return !(sVisLeft > tile.sourceRect.right
      || tile.sourceRect.left > sVisRight
      || sVisTop > tile.sourceRect.bottom
      || tile.sourceRect.top > sVisBottom
    )
  }

  private fun viewToSourceX(vx: Float): Float {
    return (vx - screenTranslate.x) / scaleState.value
  }

  private fun viewToSourceY(vy: Float): Float {
    return (vy - screenTranslate.y) / scaleState.value
  }

  private fun sourceToViewX(sx: Float): Float {
    return sx * scaleState.value + screenTranslate.x
  }

  private fun sourceToViewY(sy: Float): Float {
    return sy * scaleState.value + screenTranslate.y
  }

  internal fun calculateInSampleSize(sourceWidth: Int, sourceHeight: Int, scale: Float): Int {
    var modifiedScale = scale

    if (minTileDpiState > 0) {
      val metrics: DisplayMetrics = getResources().displayMetrics
      val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
      modifiedScale *= (minTileDpiState / averageDpi)
    }

    val reqWidth = (sourceWidth * modifiedScale).toInt()
    val reqHeight = (sourceHeight * modifiedScale).toInt()

    var inSampleSize = 1
    if (reqWidth == 0 || reqHeight == 0) {
      return 32
    }

    if (sourceHeight > reqHeight || sourceWidth > reqWidth) {
      val heightRatio = Math.round(sourceHeight.toFloat() / reqHeight.toFloat())
      val widthRatio = Math.round(sourceWidth.toFloat() / reqWidth.toFloat())

      inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
    }

    var power = 1
    while (power * 2 < inSampleSize) {
      power *= 2
    }

    return power
  }

  private fun initialiseTileMap(
    sourceWidth: Int,
    sourceHeight: Int,
    maxTileWidth: Int,
    maxTileHeight: Int,
    availableWidth: Int,
    availableHeight: Int,
    fullImageSampleSize: Int
  ) {
    tileMap.clear()
    var sampleSize = fullImageSampleSize
    var xTiles = 1
    var yTiles = 1

    while (true) {
      var sTileWidth: Int = sourceWidth / xTiles
      var sTileHeight: Int = sourceHeight / yTiles
      var subTileWidth = sTileWidth / sampleSize
      var subTileHeight = sTileHeight / sampleSize

      while (
        subTileWidth + xTiles + 1 > maxTileWidth ||
        (subTileWidth > availableWidth * 1.25 && sampleSize < fullImageSampleSize)
      ) {
        xTiles += 1
        sTileWidth = sourceWidth / xTiles
        subTileWidth = sTileWidth / sampleSize
      }

      while (
        subTileHeight + yTiles + 1 > maxTileHeight ||
        (subTileHeight > availableHeight * 1.25 && sampleSize < fullImageSampleSize)
      ) {
        yTiles += 1
        sTileHeight = sourceHeight / yTiles
        subTileHeight = sTileHeight / sampleSize
      }

      val tileGrid = ArrayList<Tile>(xTiles * yTiles)

      for (x in 0 until xTiles) {
        for (y in 0 until yTiles) {
          val tile = Tile()
          tile.sampleSize = sampleSize
          tile.visible = sampleSize == fullImageSampleSize

          tile.sourceRect.set(
            left = x * sTileWidth,
            top = y * sTileHeight,
            right = if (x == xTiles - 1) sourceWidth else (x + 1) * sTileWidth,
            bottom = if (y == yTiles - 1) sourceHeight else (y + 1) * sTileHeight
          )

          tile.screenRect.set(0, 0, 0, 0)
          tile.fileSourceRect.set(tile.sourceRect)

          tileGrid.add(tile)
        }
      }

      tileMap[sampleSize] = tileGrid

      if (sampleSize == 1) {
        break
      }

      sampleSize /= 2
    }
  }

  fun fitToBounds(center: Boolean) {
    satTemp.scale = scaleState.value
    satTemp.screenTranslate.set(
      screenTranslate.x.toFloat(),
      screenTranslate.y.toFloat()
    )

    fitToBounds(center, satTemp)

    scaleState.value = satTemp.scale
    screenTranslate.set(
      satTemp.screenTranslate.x.toInt(),
      satTemp.screenTranslate.y.toInt()
    )

    if (needInitScreenTranslate) {
      needInitScreenTranslate = false

      screenTranslate.set(
        vTranslateForSCenter(
          sCenterX = (sourceWidth / 2).toFloat(),
          sCenterY = (sourceHeight / 2).toFloat(),
          scale = scaleState.value
        )
      )
    }
  }

  private fun vTranslateForSCenter(sCenterX: Float, sCenterY: Float, scale: Float): PointF {
    val vxCenter: Int = availableWidth / 2
    val vyCenter: Int = availableHeight / 2

    satTemp.scale = scale
    satTemp.screenTranslate.set(vxCenter - sCenterX * scale, vyCenter - sCenterY * scale)

    fitToBounds(true, satTemp)
    return satTemp.screenTranslate
  }

  private fun fitToBounds(shouldCenter: Boolean, sat: ScaleAndTranslate) {
    var center = shouldCenter
//    if (panLimit == PAN_LIMIT_OUTSIDE && isReady()) {
//      center = false
//    }

    check(availableWidth > 0) { "Bad availableWidth" }
    check(availableHeight > 0) { "Bad availableHeight" }

    val vTranslate: PointF = sat.screenTranslate
    val scale: Float = limitedScale(sat.scale)
    val scaleWidth: Float = scale * sourceWidth
    val scaleHeight: Float = scale * sourceHeight

    /*if (panLimit == PAN_LIMIT_CENTER && isReady()) {
      vTranslate.x = Math.max(vTranslate.x, availableWidth / 2 - scaleWidth)
      vTranslate.y = Math.max(vTranslate.y, availableHeight / 2 - scaleHeight)
    } else */
    if (center) {
      vTranslate.x = Math.max(vTranslate.x, availableWidth - scaleWidth)
      vTranslate.y = Math.max(vTranslate.y, availableHeight - scaleHeight)
    } else {
      vTranslate.x = Math.max(vTranslate.x, -scaleWidth)
      vTranslate.y = Math.max(vTranslate.y, -scaleHeight)
    }

    // Asymmetric padding adjustments
//    val xPaddingRatio = if (getPaddingLeft() > 0 || getPaddingRight() > 0) {
//      getPaddingLeft() / (getPaddingLeft() + getPaddingRight()) as Float
//    } else {
//      0.5f
//    }
//    val yPaddingRatio = if (getPaddingTop() > 0 || getPaddingBottom() > 0) {
//      getPaddingTop() / (getPaddingTop() + getPaddingBottom()) as Float
//    } else {
//      0.5f
//    }

    val xPaddingRatio = 0.5f
    val yPaddingRatio = 0.5f

    val maxTx: Float
    val maxTy: Float

    /*if (panLimit == PAN_LIMIT_CENTER && isReady()) {
      maxTx = Math.max(0, availableWidth / 2).toFloat()
      maxTy = Math.max(0, availableHeight / 2).toFloat()
    } else */
    if (center) {
      maxTx = Math.max(0f, (availableWidth - scaleWidth) * xPaddingRatio)
      maxTy = Math.max(0f, (availableHeight - scaleHeight) * yPaddingRatio)
    } else {
      maxTx = Math.max(0, availableWidth).toFloat()
      maxTy = Math.max(0, availableHeight).toFloat()
    }
    vTranslate.x = Math.min(vTranslate.x, maxTx)
    vTranslate.y = Math.min(vTranslate.y, maxTy)
    sat.scale = scale
  }

  private fun limitedScale(targetScale: Float): Float {
    val minScale = Math.min(
      availableWidth / sourceWidth.toFloat(),
      availableHeight / sourceHeight.toFloat()
    )

    var resultScale = targetScale
    resultScale = Math.max(minScale, resultScale)
    resultScale = Math.min(3f, resultScale)
    return resultScale
  }

  internal fun sourceToViewRect(source: RectMut, target: RectMut) {
    target.set(
      sourceToViewX(source.left.toFloat()).toInt(),
      sourceToViewY(source.top.toFloat()).toInt(),
      sourceToViewX(source.right.toFloat()).toInt(),
      sourceToViewY(source.bottom.toFloat()).toInt()
    )
  }

  private fun getResources(): Resources = context.resources

  fun setMatrixArray(
    srcArray: FloatArray,
    f0: Float,
    f1: Float,
    f2: Float,
    f3: Float,
    f4: Float,
    f5: Float,
    f6: Float,
    f7: Float
  ) {
    srcArray[0] = f0
    srcArray[1] = f1
    srcArray[2] = f2
    srcArray[3] = f3
    srcArray[4] = f4
    srcArray[5] = f5
    srcArray[6] = f6
    srcArray[7] = f7
  }

  companion object {
    private const val TAG = "ComposeSubsamplingImageState"
  }

}