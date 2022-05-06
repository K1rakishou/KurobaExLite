package com.github.k1rakishou.kurobaexlite.features.drawer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
  navigationRouter: NavigationRouter
) {
  val componentActivity = LocalComponentActivity.current
  val density = LocalDensity.current
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()

  val drawerScreen = remember { BookmarksScreen(componentActivity, navigationRouter) }
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

  InitDrawerState(drawerWidth, drawerSwipeState, density)

  val clickable = remember(key1 = drawerVisibility) {
    when (drawerVisibility) {
      DrawerVisibility.Closed -> false
      DrawerVisibility.Closing,
      is DrawerVisibility.Fling,
      is DrawerVisibility.Drag,
      DrawerVisibility.Opened,
      DrawerVisibility.Opening -> true
    }
  }

  val clickableModifier = remember(key1 = clickable) {
    if (clickable) {
      Modifier.kurobaClickable(hasClickIndication = false) { globalUiInfoManager.closeDrawer() }
    } else {
      Modifier
    }
  }

  var prevDragX by remember { mutableStateOf(0f) }
  val offsetAnimated by drawerSwipeState.offset
  val bgAlphaAnimated = remember(key1 = offsetAnimated, key2 = drawerWidth) {
    val animationProgress = (1f - (offsetAnimated.absoluteValue / drawerWidth.toFloat())).coerceIn(0f, 1f)
    return@remember lerpFloat(0f, .8f, animationProgress)
  }

  LaunchedEffect(
    key1 = drawerVisibility,
    block = {
      if (drawerSwipeState.isAnimationRunning) {
        return@LaunchedEffect
      }

      when (drawerVisibility) {
        is DrawerVisibility.Drag -> {
          processDragEvents(
            prevDragX = prevDragX,
            dragEventsCombined = dragEventsCombined,
            drawerSwipeState = drawerSwipeState,
            offsetAnimated = offsetAnimated,
            drawerWidth = drawerWidth,
            globalUiInfoManager = globalUiInfoManager,
            updatePrevDragX = { newDragX -> prevDragX = newDragX }
          )
        }
        is DrawerVisibility.Fling -> {
          velocityTracker.resetTracking()

          val velocityX = drawerVisibility.velocity.x
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
      .background(bgColorWithAlpha)
      .then(clickableModifier)
  ) {
    Box(
      modifier = Modifier
        .width(drawerWidthDp)
        .fillMaxHeight()
        .absoluteOffset { IntOffset(offsetAnimated.toInt(), 0) }
    ) {
      RouterHost(
        navigationRouter = navigationRouter,
        defaultScreen = { drawerScreen.Content() }
      )
    }
  }
}

private suspend fun processDragEvents(
  prevDragX: Float,
  dragEventsCombined: MutableList<DrawerVisibility.Drag>,
  drawerSwipeState: DrawerSwipeState,
  offsetAnimated: Float,
  drawerWidth: Int,
  globalUiInfoManager: GlobalUiInfoManager,
  updatePrevDragX: (newDragX: Float) -> Unit
) {
  var currentPrevDragX = prevDragX
  var endedNormally = false

  try {
    dragEventsCombined.mutableIteration { mutableIterator, dragEvent ->
      mutableIterator.remove()

      if (dragEvent.isDragging) {
        val dragDelta = dragEvent.dragX - currentPrevDragX
        currentPrevDragX = dragEvent.dragX

        velocityTracker.addPosition(
          timeMillis = dragEvent.time,
          position = Offset(x = dragEvent.dragX, y = 0f)
        )
        drawerSwipeState.performDrag(dragDelta)
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
      dragEventsCombined.clear()
    }

    updatePrevDragX(currentPrevDragX)
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun InitDrawerState(
  drawerWidth: Int,
  drawerSwipeState: DrawerSwipeState,
  density: Density
) {
  val anchors = remember {
    mapOf(
      Pair(-(drawerWidth.toFloat()), DrawerSwipeState.State.Closed),
      Pair(0f, DrawerSwipeState.State.Opened),
    )
  }

  val thresholds: (from: DrawerSwipeState.State, to: DrawerSwipeState.State) -> ThresholdConfig = remember { { _, _ -> FixedThreshold(56.dp) } }
  val resistance = remember { SwipeableDefaults.resistanceConfig(anchors.keys) }

  drawerSwipeState.ensureInit(anchors)
  LaunchedEffect(anchors, drawerSwipeState) {
    val oldAnchors = drawerSwipeState.anchors
    drawerSwipeState.anchors = anchors
    drawerSwipeState.resistance = resistance
    drawerSwipeState.thresholds = { a, b ->
      val from = anchors.getValue(a)
      val to = anchors.getValue(b)

      with(thresholds(from, to)) { density.computeThreshold(a, b) }
    }

    with(density) {
      drawerSwipeState.velocityThreshold = SwipeableDefaults.VelocityThreshold.toPx()
    }
    drawerSwipeState.processNewAnchors(oldAnchors, anchors)
  }
}