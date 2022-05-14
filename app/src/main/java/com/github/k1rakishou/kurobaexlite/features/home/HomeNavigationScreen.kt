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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import kotlinx.coroutines.flow.StateFlow

abstract class HomeNavigationScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreenWithToolbar(componentActivity, navigationRouter) {
  abstract val screenContentLoadedFlow: StateFlow<Boolean>

  @OptIn(ExperimentalMaterialApi::class)
  @Composable
  final override fun Content() {
    var bgColor by remember { mutableStateOf(Color.Unspecified) }

    BoxWithConstraints {
      val currentUiLayoutMode by globalUiInfoManager.currentUiLayoutModeState.collectAsState()
      val topHomeNavigationScreen = topHomeNavigationScreenExceptDefault()
      val screenWidth = constraints.maxWidth

      val modifier = if (
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
            val alpha = (1f - (currentOffset / screenWidth.toFloat())).coerceIn(0f, 1f)
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
        Modifier
      }

      Box(modifier = Modifier.background(bgColor)) {
        Box(modifier = modifier) {
          HomeNavigationScreenContent()
        }
      }
    }
  }

  @Composable
  abstract fun HomeNavigationScreenContent()

  private fun topHomeNavigationScreenExceptDefault(): HomeNavigationScreen? {
    val navigationScreensStack = navigationRouter.navigationScreensStack
    if (navigationScreensStack.isEmpty()) {
      return null
    }

    return navigationScreensStack.last() as? HomeNavigationScreen
  }

  private enum class Anchors {
    Visible,
    Hidden
  }
}