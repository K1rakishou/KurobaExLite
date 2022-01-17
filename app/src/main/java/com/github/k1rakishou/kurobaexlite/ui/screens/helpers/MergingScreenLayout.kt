package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class MergingScreenLayout(
  componentActivity: ComponentActivity,
  private val orientation: Orientation,
  private val childScreens: List<ComposeScreen>
) : ComposeScreen(componentActivity) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val weight = 1f / childScreens.size.toFloat()

    when (orientation) {
      Orientation.Horizontal -> {
        Row(modifier = Modifier.fillMaxSize()) {
          for (childScreen in childScreens) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .weight(weight = weight)
            ) {
              childScreen.Content()
            }
          }
        }
      }
      Orientation.Vertical -> {
        Column(modifier = Modifier.fillMaxSize()) {
          for (childScreen in childScreens) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .weight(weight = weight)
            ) {
              childScreen.Content()
            }
          }
        }
      }
    }
  }

  enum class Orientation {
    Horizontal,
    Vertical
  }

  companion object {
    val SCREEN_KEY = ScreenKey("MergingScreenLayout")
  }
}