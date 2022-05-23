package com.github.k1rakishou.kurobaexlite.features.drawer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.ThresholdConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreen
import com.github.k1rakishou.kurobaexlite.helpers.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.mutableIteration
import com.github.k1rakishou.kurobaexlite.helpers.unreachable
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaSwipeableState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalComponentActivity
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val bgColor = Color.Black
private val flingVelocity = 5000f
private val velocityTracker = VelocityTracker()

@OptIn(ExperimentalMaterialApi::class)
class DrawerSwipeState(
  initialValue: State,
  animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec,
  confirmStateChange: (State) -> Boolean = { true },
) : KurobaSwipeableState<DrawerSwipeState.State>(
  initialValue = initialValue,
  animationSpec = animationSpec,
  confirmStateChange = confirmStateChange
) {

  @OptIn(ExperimentalMaterialApi::class)
  @Composable
  fun InitDrawerState(
    drawerWidth: Int,
    density: Density
  ) {
    val anchors = remember {
      mapOf(
        Pair(-(drawerWidth.toFloat()), State.Closed),
        Pair(0f, State.Opened),
      )
    }

    val thresholds: (from: State, to: State) -> ThresholdConfig = remember { { _, _ -> FixedThreshold(56.dp) } }
    val resistance = remember { SwipeableDefaults.resistanceConfig(anchors.keys) }

    this@DrawerSwipeState.ensureInit(anchors)

    LaunchedEffect(anchors, this@DrawerSwipeState) {
      val oldAnchors = this@DrawerSwipeState.anchors
      this@DrawerSwipeState.anchors = anchors
      this@DrawerSwipeState.resistance = resistance
      this@DrawerSwipeState.thresholds = { a, b ->
        val from = anchors.getValue(a)
        val to = anchors.getValue(b)

        with(thresholds(from, to)) { density.computeThreshold(a, b) }
      }

      with(density) {
        this@DrawerSwipeState.velocityThreshold = SwipeableDefaults.VelocityThreshold.toPx()
      }

      this@DrawerSwipeState.processNewAnchors(oldAnchors, anchors)
    }
  }

  enum class State {
    Closed,
    Opened;

    companion object {
      fun fromDrawerVisibility(drawerWidth: Int, drawerVisibility: DrawerVisibility): State {
        return when (drawerVisibility) {
          DrawerVisibility.Closed,
          DrawerVisibility.Closing -> Closed
          is DrawerVisibility.Fling -> Opened
          is DrawerVisibility.Drag -> if (drawerVisibility.dragX >= (drawerWidth / 2f)) Opened else Closed
          DrawerVisibility.Opened,
          DrawerVisibility.Opening -> Opened
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreenDrawerLayout(
  drawerWidth: Int,
  navigationRouterProvider: () -> NavigationRouter
) {
  val componentActivity = LocalComponentActivity.current
  val density = LocalDensity.current
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()

  val drawerScreen = remember { BookmarksScreen(componentActivity, navigationRouterProvider()) }
  val drawerWidthDp = with(density) { remember(key1 = drawerWidth) { drawerWidth.toDp() } }

  val dragEventsCombined = remember { mutableListOf<DrawerVisibility.Drag>() }
  var drawerVisibilityMut by remember { mutableStateOf<DrawerVisibility>(globalUiInfoManager.currentDrawerVisibility) }
  val drawerVisibility = drawerVisibilityMut

  LaunchedEffect(
    key1 = Unit,
    block = {
      globalUiInfoManager.drawerVisibilityFlow.collect { event ->
        if (event is DrawerVisibility.Drag) {
          dragEventsCombined += event
        }

        drawerVisibilityMut = event
      }
    }
  )

  val drawerSwipeState = remember {
    DrawerSwipeState(
      initialValue = DrawerSwipeState.State.fromDrawerVisibility(
        drawerWidth = drawerWidth,
        drawerVisibility = drawerVisibility
      )
    )
  }

  drawerSwipeState.InitDrawerState(drawerWidth, density)

  val isFullyClosed by remember(key1 = drawerVisibility) {
    derivedStateOf {
      when (drawerVisibility) {
        DrawerVisibility.Closed -> true
        DrawerVisibility.Closing,
        is DrawerVisibility.Fling,
        is DrawerVisibility.Drag,
        DrawerVisibility.Opened,
        DrawerVisibility.Opening -> false
      }
    }
  }

  if (isFullyClosed) {
    return
  }

  val clickable by remember(key1 = drawerVisibility) {
    derivedStateOf {
      when (drawerVisibility) {
        DrawerVisibility.Closed -> false
        DrawerVisibility.Closing,
        is DrawerVisibility.Fling,
        is DrawerVisibility.Drag,
        DrawerVisibility.Opened,
        DrawerVisibility.Opening -> true
      }
    }
  }

  val clickableModifier = remember(key1 = clickable) {
    if (clickable) {
      Modifier.kurobaClickable(hasClickIndication = false) { globalUiInfoManager.closeDrawer() }
    } else {
      Modifier
    }
  }

  val drawerOffsetFloat by drawerSwipeState.offset
  val drawerOffset = drawerOffsetFloat.roundToInt()

  val bgAlphaAnimated = remember(key1 = drawerOffset, key2 = drawerWidth) {
    val animationProgress = (1f - (drawerOffset.absoluteValue / drawerWidth.toFloat())).coerceIn(0f, 1f)
    return@remember lerpFloat(0f, .8f, animationProgress)
  }

  var currentPrevDragX by remember { mutableStateOf<Float>(Float.NaN) }

  LaunchedEffect(
    key1 = drawerVisibility,
    block = {
      if (drawerSwipeState.isAnimationRunning) {
        dragEventsCombined.clear()
        return@LaunchedEffect
      }

      when (drawerVisibility) {
        is DrawerVisibility.Drag -> {
          processDragEvents(
            dragEventsCombined = dragEventsCombined,
            drawerSwipeState = drawerSwipeState,
            offsetAnimated = drawerOffsetFloat,
            drawerWidth = drawerWidth,
            globalUiInfoManager = globalUiInfoManager,
            getCurrentPrevDragX = { currentPrevDragX },
            updateCurrentPrevDragX = { dragx -> currentPrevDragX = dragx }
          )
        }
        is DrawerVisibility.Fling -> {
          velocityTracker.resetTracking()
          currentPrevDragX = Float.NaN

          val velocityX = drawerVisibility.velocity.x
          val opening = velocityX >= 0f
          val canPerformFling = velocityX.absoluteValue > flingVelocity
          val animationProgress = (1f - (drawerOffset.absoluteValue / drawerWidth.toFloat())).coerceIn(0f, 1f)

          endDragWithFling(
            opening = opening,
            canPerformFling = canPerformFling,
            drawerSwipeState = drawerSwipeState,
            velocityX = velocityX,
            globalUiInfoManager = globalUiInfoManager,
            animationProgress = animationProgress
          )
        }
        else -> {
          when (drawerVisibility) {
            DrawerVisibility.Closed -> {
              drawerSwipeState.snapTo(DrawerSwipeState.State.Closed)
            }
            DrawerVisibility.Opened -> {
              drawerSwipeState.snapTo(DrawerSwipeState.State.Opened)
            }
            DrawerVisibility.Closing -> {
              drawerSwipeState.animateTo(DrawerSwipeState.State.Closed)
              globalUiInfoManager.closeDrawer(withAnimation = false)
            }
            DrawerVisibility.Opening -> {
              drawerSwipeState.animateTo(DrawerSwipeState.State.Opened)
              globalUiInfoManager.openDrawer(withAnimation = false)
            }
            is DrawerVisibility.Fling -> unreachable()
            is DrawerVisibility.Drag -> unreachable()
          }
        }
      }
    }
  )

  val bgColorWithAlpha = remember(key1 = bgAlphaAnimated) { bgColor.copy(alpha = bgAlphaAnimated) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawBehind { drawRect(bgColorWithAlpha) }
      .then(clickableModifier)
  ) {
    Box(
      modifier = Modifier
        .width(drawerWidthDp)
        .fillMaxHeight()
        .absoluteOffset { IntOffset(drawerOffset, 0) }
    ) {
      val router = remember { navigationRouterProvider() }

      RouterHost(
        navigationRouter = router,
        defaultScreenFunc = { drawerScreen }
      )
    }
  }
}

private suspend fun processDragEvents(
  dragEventsCombined: MutableList<DrawerVisibility.Drag>,
  drawerSwipeState: DrawerSwipeState,
  offsetAnimated: Float,
  drawerWidth: Int,
  globalUiInfoManager: GlobalUiInfoManager,
  getCurrentPrevDragX: () -> Float,
  updateCurrentPrevDragX: (Float) -> Unit
) {
  var endedNormally = false

  try {
    dragEventsCombined.mutableIteration { mutableIterator, dragEvent ->
      mutableIterator.remove()

      if (dragEvent.isDragging) {
        if (getCurrentPrevDragX().isNaN()) {
          updateCurrentPrevDragX(dragEvent.dragX)
          return@mutableIteration true
        }

        val dragDelta = dragEvent.dragX - getCurrentPrevDragX()

        velocityTracker.addPosition(
          timeMillis = dragEvent.time,
          position = Offset(x = dragEvent.dragX, y = 0f)
        )

        drawerSwipeState.performDrag(dragDelta)
        updateCurrentPrevDragX(dragEvent.dragX)

        return@mutableIteration true
      } else {
        val velocityX = velocityTracker.calculateVelocity().x
        if (velocityX == 0f) {
          velocityTracker.calculateVelocity()
        }

        velocityTracker.resetTracking()
        val opening = velocityX >= 0f
        val canPerformFling = velocityX.absoluteValue > flingVelocity
        val animationProgress = (1f - (offsetAnimated.absoluteValue / drawerWidth.toFloat())).coerceIn(0f, 1f)

        endDragWithFling(
          opening = opening,
          canPerformFling = canPerformFling,
          drawerSwipeState = drawerSwipeState,
          velocityX = velocityX,
          globalUiInfoManager = globalUiInfoManager,
          animationProgress = animationProgress
        )

        endedNormally = true
        return@mutableIteration false
      }
    }
  } finally {
    if (endedNormally) {
      updateCurrentPrevDragX(Float.NaN)
      dragEventsCombined.clear()
    }
  }
}

private suspend fun endDragWithFling(
  opening: Boolean,
  canPerformFling: Boolean,
  drawerSwipeState: DrawerSwipeState,
  velocityX: Float,
  globalUiInfoManager: GlobalUiInfoManager,
  animationProgress: Float
) {
  if (opening) {
    if (canPerformFling) {
      drawerSwipeState.performFling(velocityX)
      globalUiInfoManager.openDrawer(withAnimation = false)
    } else {
      if (animationProgress > 0.5f) {
        globalUiInfoManager.openDrawer(withAnimation = true)
      } else {
        globalUiInfoManager.closeDrawer(withAnimation = true)
      }
    }
  } else {
    if (canPerformFling) {
      drawerSwipeState.performFling(velocityX)
      globalUiInfoManager.closeDrawer(withAnimation = false)
    } else {
      if (animationProgress > 0.5f) {
        globalUiInfoManager.openDrawer(withAnimation = true)
      } else {
        globalUiInfoManager.closeDrawer(withAnimation = true)
      }
    }
  }
}