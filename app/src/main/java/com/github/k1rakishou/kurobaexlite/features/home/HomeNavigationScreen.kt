package com.github.k1rakishou.kurobaexlite.features.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.quantize
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import kotlinx.coroutines.flow.StateFlow

abstract class HomeNavigationScreen<ToolbarType : KurobaChildToolbar>(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreenWithToolbar<ToolbarType>(componentActivity, navigationRouter) {
  abstract val screenContentLoadedFlow: StateFlow<Boolean>

  protected open val dragToCloseEnabledState: MutableState<Boolean> = mutableStateOf(true)
  val dragToCloseEnabled: Boolean
    get() = dragToCloseEnabledState.value

  private val _animationProgress = mutableStateOf<Float>(0f)
  val animationProgress: State<Float>
    get() = _animationProgress

  @Composable
  final override fun Content() {
    SwipeableScreen {
      HomeNavigationScreenContent()
    }
  }

  @Composable
  abstract fun HomeNavigationScreenContent()

  @OptIn(ExperimentalMaterialApi::class)
  @Composable
  private fun SwipeableScreen(content: @Composable () -> Unit) {
    BoxWithConstraints {
      var bgColor by remember { mutableStateOf(Color.Unspecified) }

      val currentUiLayoutMode by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
      val currentDragToCloseEnabled by dragToCloseEnabledState
      val topHomeNavigationScreen = topHomeNavigationScreenExceptDefault()
      val screenWidth = constraints.maxWidth

      val modifier = if (
        currentDragToCloseEnabled &&
        currentUiLayoutMode == MainUiLayoutMode.Phone &&
        topHomeNavigationScreen != null &&
        topHomeNavigationScreen.screenKey == screenKey
      ) {
        val swipeableState = rememberSwipeableState(initialValue = Anchors.Visible)
        val anchors = remember(key1 = screenWidth) {
          mapOf(
            0f to Anchors.Visible,
            screenWidth.toFloat() to Anchors.Hidden
          )
        }

        val isAnimationRunning = swipeableState.isAnimationRunning
        val currentValue = swipeableState.currentValue
        val currentOffset by swipeableState.offset

        LaunchedEffect(
          key1 = currentValue,
          block = {
            if (currentValue == Anchors.Hidden) {
              navigationRouter.popScreen(
                newComposeScreen = this@HomeNavigationScreen,
                withAnimation = false
              )
            }
          }
        )

        LaunchedEffect(
          key1 = currentOffset.toInt(),
          key2 = screenWidth,
          block = {
            val progress = 1f - ((currentOffset / screenWidth.toFloat())
              .coerceIn(-1f, 1f))
              .quantize(AppConstants.Transition.TransitionDesireableFps)

            _animationProgress.value = progress

            val alpha = lerpFloat(0f, .8f, progress)
            bgColor = Color.Black.copy(alpha = alpha)
          }
        )

        DisposableEffect(
          key1 = Unit,
          effect = { onDispose { bgColor = Color.Unspecified } }
        )

        Modifier
          .swipeable(
            state = swipeableState,
            anchors = anchors,
            orientation = Orientation.Horizontal,
            thresholds = { _, _ -> FractionalThreshold(.5f) },
          )
          .absoluteOffset { IntOffset(currentOffset.toInt(), 0) }
          .consumeClicks(enabled = isAnimationRunning)
      } else {
        if (
          currentUiLayoutMode == MainUiLayoutMode.Split &&
          topHomeNavigationScreen != null &&
          topHomeNavigationScreen.screenKey == screenKey
        ) {
          LaunchedEffect(
            key1 = Unit,
            block = { _animationProgress.value = 1f }
          )
        }

        Modifier
      }

      Box(modifier = Modifier.background(bgColor)) {
        Box(modifier = modifier) {
          content()
        }
      }
    }
  }

  private fun topHomeNavigationScreenExceptDefault(): HomeNavigationScreen<ToolbarType>? {
    val navigationScreensStack = navigationRouter.navigationScreensStack
    if (navigationScreensStack.isEmpty()) {
      return null
    }

    return navigationScreensStack.last() as? HomeNavigationScreen<ToolbarType>
  }

  private enum class Anchors {
    Visible,
    Hidden
  }
}