package com.github.k1rakishou.kurobaexlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.k1rakishou.kurobaexlite.ui.theme.KurobaExLiteTheme

class MainActivity : ComponentActivity() {
  private val viewModel by viewModels<MainActivityViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        viewModel.navigation.popTopScreen()
      }
    })

    setContent {
      KurobaExLiteTheme {
        BuildScreens()
      }
    }
  }

  @Composable
  private fun BuildScreens() {
    val screenUpdateState by viewModel.navigation.screenUpdatesFlow.collectAsState()
    val screenUpdate = screenUpdateState
      ?: return

    val screenContentBuilder = screenUpdate.screen.screenContentBuilder

    ScreenTransition(
      screenUpdate = screenUpdate,
      onTransitionFinished = { executedScreenUpdate ->
        if (executedScreenUpdate.isPop() && !viewModel.navigation.hasScreens()) {
          finish()
        }
      }
    ) {
      screenContentBuilder.invoke()
    }
  }

  @Composable
  private fun ScreenTransition(
    screenUpdate: Navigation.ScreenUpdate,
    onTransitionFinished: (Navigation.ScreenUpdate) -> Unit,
    content: @Composable () -> Unit
  ) {
    val duration = 250
    var scaleAnimated by remember { mutableStateOf(0f) }
    var alphaAnimated by remember { mutableStateOf(0f) }

    LaunchedEffect(
      key1 = screenUpdate,
      block = {
        when (screenUpdate) {
          is Navigation.ScreenUpdate.Push -> {
            animate(
              initialValue = 0f,
              targetValue = 1f,
              initialVelocity = 0f,
              animationSpec = tween(duration)
            ) { animationProgress, _ ->
              scaleAnimated = 1f - (0.5f - (animationProgress / 2f))
              alphaAnimated = animationProgress
            }
          }
          is Navigation.ScreenUpdate.PopTop -> {
            animate(
              initialValue = 1f,
              targetValue = 0f,
              initialVelocity = 0f,
              animationSpec = tween(duration)
            ) { animationProgress, _ ->
              scaleAnimated = 1f - (0.5f - (animationProgress / 2f))
              alphaAnimated = animationProgress
            }
          }
        }

        onTransitionFinished(screenUpdate)
      }
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(
          alpha = alphaAnimated,
          scaleX = scaleAnimated,
          scaleY = scaleAnimated
        )
    ) {
      content()
    }
  }

}