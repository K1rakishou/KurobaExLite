package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlinx.coroutines.launch

private val sheetHeaderHeightDp = 32.dp

@Composable
fun rememberKurobaBottomSheetState(
  defaultSheetValue: KurobaBottomSheetValue = KurobaBottomSheetValue.Collapsed
): KurobaBottomSheetState {
  return rememberSaveable(
    saver = KurobaBottomSheetState.Saver(),
    init = { KurobaBottomSheetState(defaultSheetValue) }
  )
}

@OptIn(ExperimentalMaterialApi::class)
@Stable
class KurobaBottomSheetState(
  defaultSheetValue: KurobaBottomSheetValue
) : SwipeableState<KurobaBottomSheetValue>(defaultSheetValue) {
  var fullAvailableHeight: Float = 0f

  val isExpanded: Boolean
    get() = currentValue == KurobaBottomSheetValue.Expanded && targetValue == KurobaBottomSheetValue.Expanded

  val isExpandedOrExpanding: Boolean
    get() = currentValue == KurobaBottomSheetValue.Expanded || targetValue == KurobaBottomSheetValue.Expanded

  val isOpenedOrOpening: Boolean
    get() = currentValue == KurobaBottomSheetValue.Opened || targetValue == KurobaBottomSheetValue.Opened

  val isCollapsedOrCollapsing: Boolean
    get() = currentValue == KurobaBottomSheetValue.Collapsed || targetValue == KurobaBottomSheetValue.Collapsed

  val isNotCollapsed: Boolean
    get() = isExpandedOrExpanding || isOpenedOrOpening

  val offsetValueAsProgress: Float
    get() {
      if (fullAvailableHeight <= 0f) {
        return 0f
      }

      return (offset.value / fullAvailableHeight).coerceIn(0f, 1f)
    }

  suspend fun open() {
    animateTo(KurobaBottomSheetValue.Opened)
  }

  suspend fun expand() {
    animateTo(KurobaBottomSheetValue.Expanded)
  }

  suspend fun collapse() {
    animateTo(KurobaBottomSheetValue.Collapsed)
  }

  suspend fun onBackPressed(): Boolean {
    if (isNotCollapsed) {
      collapse()
      return true
    }

    return false
  }

  companion object {
    fun Saver() = androidx.compose.runtime.saveable.Saver<KurobaBottomSheetState, KurobaBottomSheetValue>(
      save = { it.currentValue },
      restore = { KurobaBottomSheetState(it) }
    )
  }

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun KurobaBottomSheet(
  modifier: Modifier = Modifier,
  kurobaBottomSheetState: KurobaBottomSheetState = rememberKurobaBottomSheetState(),
  sheetPeekHeight: Dp = 88.dp,
  @FloatRange(0.0, 1.0) openedHeightPercentage: Float = 0.35f,
  scrimBgColor: Color = Color.Black,
  sheetGesturesEnabled: Boolean = true,
  sheetBackgroundColor: Color = Color.White,
  sheetContent: @Composable ColumnScope.(PaddingValues) -> Unit,
  content: @Composable (PaddingValues) -> Unit
) {
  val density = LocalDensity.current
  val insets = LocalWindowInsets.current

  val sheetHeaderHeight = with(density) { sheetHeaderHeightDp.roundToPx() }

  BoxWithConstraints(modifier = modifier) {
    val topInsetPx = with(density) { insets.top.toPx() }
    val fullHeight = constraints.maxHeight.toFloat()
    kurobaBottomSheetState.fullAvailableHeight = fullHeight

    val peekHeightPx = with(LocalDensity.current) { sheetPeekHeight.toPx() }
    var bottomSheetHeight by remember { mutableStateOf(fullHeight) }

    val anchors = remember(key1 = insets.top) {
      mapOf(
        (fullHeight - peekHeightPx) to KurobaBottomSheetValue.Collapsed,
        (fullHeight - (fullHeight * openedHeightPercentage)) to KurobaBottomSheetValue.Opened,
        (fullHeight - bottomSheetHeight + topInsetPx) to KurobaBottomSheetValue.Expanded
      )
    }

    val nestedScrollConnection = remember { PreUpPostDownNestedScrollConnection(kurobaBottomSheetState) }
    val coroutineScope = rememberCoroutineScope()

    val swipeable = Modifier
      .nestedScroll(nestedScrollConnection)
      .swipeable(
        state = kurobaBottomSheetState,
        anchors = anchors,
        orientation = Orientation.Vertical,
        enabled = sheetGesturesEnabled,
        resistance = null
      )

    BottomSheetScaffoldStack(
      body = {
        val scrimBgColorWithAlpha = remember { scrimBgColor.copy(alpha = 0.7f) }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {}
        ) {
          content(PaddingValues(bottom = sheetPeekHeight))

          Scrim(
            open = kurobaBottomSheetState.currentValue.order > KurobaBottomSheetValue.Collapsed.order,
            onClose = { coroutineScope.launch { kurobaBottomSheetState.collapse() } },
            fraction = { 1f - (kurobaBottomSheetState.offset.value / fullHeight).coerceIn(0f, 1f) },
            color = scrimBgColorWithAlpha
          )

          if (kurobaBottomSheetState.isExpandedOrExpanding && insets.top > 0.dp) {
            Box(
              modifier = Modifier
                .height(insets.top)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .drawBehind { drawRect(sheetBackgroundColor) }
            )
          }
        }
      },
      bottomSheet = {
        Column(
          modifier = swipeable
            .fillMaxWidth()
            .requiredHeightIn(min = sheetPeekHeight)
            .onGloballyPositioned { bottomSheetHeight = it.size.height.toFloat() },
        ) {
          SheetHeader(
            sheetHeaderHeight = sheetHeaderHeight.toFloat(),
            fullHeight = with(density) { fullHeight - insets.top.toPx() },
            sheetBackgroundColor = sheetBackgroundColor,
            kurobaBottomSheetState = kurobaBottomSheetState
          )

          Column(
            modifier = Modifier.drawBehind { drawRect(sheetBackgroundColor) }
          ) {
            sheetContent(PaddingValues(bottom = insets.top))
          }
        }
      },
      bottomSheetOffset = kurobaBottomSheetState.offset,
    )
  }
}

@Composable
fun SheetHeader(
  sheetHeaderHeight: Float,
  fullHeight: Float,
  sheetBackgroundColor: Color,
  kurobaBottomSheetState: KurobaBottomSheetState
) {
  val density = LocalDensity.current

  val path = remember { Path() }
  val gapHeight = with(density) { remember { 6.dp.roundToPx().toFloat() } }

  val isSheetBackgroundDark = remember(key1 = sheetBackgroundColor) {
    ColorUtils.calculateLuminance(sheetBackgroundColor.toArgb()) < 0.5f
  }

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(sheetHeaderHeightDp),
    onDraw = {
      val progress = (kurobaBottomSheetState.offset.value / fullHeight).coerceIn(0f, 1f)
      val radius = progress * (sheetHeaderHeight - (sheetHeaderHeight / 3f))
      val cornerRadius = CornerRadius(radius, radius)

      val roundRect = RoundRect(
        left = 0f,
        top = 0f,
        right = size.width,
        bottom = size.height,
        topLeftCornerRadius = cornerRadius,
        topRightCornerRadius = cornerRadius
      )

      path.reset()
      path.addRoundRect(roundRect)
      path.close()

      clipPath(
        path = path,
        clipOp = ClipOp.Intersect
      ) {
        drawRect(sheetBackgroundColor)

        val gapWidth = this.size.width / 10f
        val topLeft = (size / 2f) - Size((gapWidth / 2f), 0f)

        translate(left = topLeft.width, top = topLeft.height) {
          val color = if (isSheetBackgroundDark) {
            Color.LightGray
          } else {
            Color.DarkGray
          }

          drawRoundRect(
            color = color,
            size = Size(gapWidth, gapHeight),
            cornerRadius = CornerRadius(gapHeight / 2f, gapHeight / 2f)
          )
        }
      }
    }
  )
}

@Stable
private operator fun Size.minus(other: Size) = Size(width - other.width, height - other.height)

@Composable
private fun Scrim(
  open: Boolean,
  onClose: () -> Unit,
  fraction: () -> Float,
  color: Color
) {
  val dismissDrawer = if (open) {
    Modifier
      .pointerInput(onClose) { detectTapGestures { onClose() } }
      .semantics(mergeDescendants = true) {
        onClick { onClose(); true }
      }
  } else {
    Modifier
  }

  Canvas(
    Modifier
      .fillMaxSize()
      .then(dismissDrawer)
  ) {
    drawRect(color, alpha = fraction())
  }
}

@Composable
private fun BottomSheetScaffoldStack(
  body: @Composable () -> Unit,
  bottomSheet: @Composable () -> Unit,
  bottomSheetOffset: State<Float>
) {
  Layout(
    modifier = Modifier.fillMaxSize(),
    content = {
      body()
      bottomSheet()
    }
  ) { measurables, constraints ->
    val bodyPlaceable = measurables.first().measure(constraints)

    layout(bodyPlaceable.width, bodyPlaceable.height) {
      bodyPlaceable.placeRelative(0, 0)

      val (sheetPlaceable) = measurables.drop(1).map {
        it.measure(constraints.copy(minWidth = 0, minHeight = 0))
      }

      val sheetOffsetY = bottomSheetOffset.value.roundToInt()

      sheetPlaceable.placeRelative(0, sheetOffsetY)
    }
  }
}

@ExperimentalMaterialApi
private class PreUpPostDownNestedScrollConnection<T>(
  private val swipeableState: SwipeableState<T>
) : NestedScrollConnection {
  private val swipeableStateMinBoundGetter by lazy {
    return@lazy (swipeableState::class as KClass<in Any>).memberProperties
      .firstOrNull { it.name == "minBound" }
      ?.also { it.isAccessible = true }
      ?: return@lazy null
  }

  private val swipeableStateMinBound: Float
    get() {
      return swipeableStateMinBoundGetter
        ?.get(swipeableState) as? Float
        ?: 0f
    }

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    val delta = available.toFloat()
    return if (delta < 0 && source == NestedScrollSource.Drag) {
      swipeableState.performDrag(delta).toOffset()
    } else {
      Offset.Zero
    }
  }

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource
  ): Offset {
    return if (source == NestedScrollSource.Drag) {
      swipeableState.performDrag(available.toFloat()).toOffset()
    } else {
      Offset.Zero
    }
  }

  override suspend fun onPreFling(available: Velocity): Velocity {
    val toFling = Offset(available.x, available.y).toFloat()
    return if (toFling < 0 && swipeableState.offset.value > swipeableStateMinBound) {
      swipeableState.performFling(velocity = toFling)
      // since we go to the anchor with tween settling, consume all for the best UX
      available
    } else {
      Velocity.Zero
    }
  }

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    swipeableState.performFling(velocity = Offset(available.x, available.y).toFloat())
    return available
  }

  private fun Float.toOffset(): Offset = Offset(0f, this)

  private fun Offset.toFloat(): Float = this.y
}


enum class KurobaBottomSheetValue(val order: Int) {
  Collapsed(0),
  Opened(1),
  Expanded(2)
}