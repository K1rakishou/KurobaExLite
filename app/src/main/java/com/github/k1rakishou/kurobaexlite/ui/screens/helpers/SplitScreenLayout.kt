package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

class SplitScreenLayout(
  componentActivity: ComponentActivity,
  private val orientation: Orientation,
  private val childScreens: List<ChildScreen>
) : ComposeScreen(componentActivity) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val weights = remember(key1 = childScreens) { calculateWeights() }

    when (orientation) {
      Orientation.Horizontal -> {
        Row(modifier = Modifier.fillMaxSize()) {
          for ((index, childScreen) in childScreens.withIndex()) {
            val weight = weights[index]

            Box(
              modifier = Modifier
                .fillMaxHeight()
                .weight(weight = weight)
            ) {
              childScreen.composeScreen.Content()
            }
          }
        }
      }
      Orientation.Vertical -> {
        Column(modifier = Modifier.fillMaxSize()) {
          for ((index, childScreen) in childScreens.withIndex()) {
            val weight = weights[index]

            Box(
              modifier = Modifier
                .fillMaxHeight()
                .weight(weight = weight)
            ) {
              childScreen.composeScreen.Content()
            }
          }
        }
      }
    }
  }

  private fun calculateWeights(): FloatArray {
    val allNulls = childScreens.all { it.weight == null }
    if (allNulls) {
      return FloatArray(childScreens.size) { 1f / childScreens.size.toFloat() }
    }

    val weights = FloatArray(childScreens.size) { 0f }
    var takenWeight = 0f
    var childrenWithWeight = 0

    childScreens.forEach { childScreen ->
      if (childScreen.weight != null) {
        takenWeight += childScreen.weight
        ++childrenWithWeight
      }
    }

    if (childrenWithWeight == 0) {
      error("No children found with weight?")
    }

    if (takenWeight > 1f) {
      error("takenWeight must be <= 1f, takenWeight=$takenWeight")
    }

    for ((index, childScreen) in childScreens.withIndex())  {
      if (childScreen.weight == null) {
        weights[index] = (1f - takenWeight) / childrenWithWeight
      } else {
        weights[index] = childScreen.weight
      }
    }

    val sum = weights.sumOf { weight -> weight.toDouble() }

    check(Math.abs(1.0 - sum) <= 0.00001) {
      "Sum of weights must be equal to 1.0! sum=$sum, weights=${weights.joinToString()}"
    }

    return weights
  }

  enum class Orientation {
    Horizontal,
    Vertical
  }

  class ChildScreen(
    val composeScreen: ComposeScreen,
    val weight: Float? = null
  )

  companion object {
    val SCREEN_KEY = ScreenKey("SplitScreenLayout")
  }
}