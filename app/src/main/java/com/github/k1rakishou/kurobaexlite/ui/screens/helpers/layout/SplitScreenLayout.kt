package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.layout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeNavigationScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SplitScreenLayout(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val childScreensBuilder: (NavigationRouter) -> List<ChildScreen>
) : HomeNavigationScreen(componentActivity, navigationRouter), ScreenLayout {

  override val screenKey: ScreenKey = SCREEN_KEY

  // TODO(KurobaEx): not implemented
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)

  override fun hasScreen(screenKey: ScreenKey): Boolean {
    return childScreensBuilder.invoke(navigationRouter)
      .any { childScreen -> childScreen.composeScreen.screenKey == screenKey }
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val chanTheme = LocalChanTheme.current
    val childScreens = remember { childScreensBuilder.invoke(navigationRouter) }
    val weights = remember(key1 = childScreens) { calculateWeights(childScreens) }

    with(boxScope) {
      Row(modifier = Modifier.fillMaxSize()) {
        for ((index, childScreen) in childScreens.withIndex()) {
          val weight = weights[index]

          Box(
            modifier = Modifier
              .fillMaxHeight()
              .weight(weight = weight)
          ) {
            childScreen.composeScreen.topChildScreen().Toolbar(this)

            if (index >= 0 && index < childScreens.size) {
              Divider(
                modifier = Modifier
                  .fillMaxHeight()
                  .width(1.dp)
                  .background(chanTheme.dividerColorCompose)
              )
            }
          }
        }
      }
    }
  }

  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val childScreens = remember { childScreensBuilder.invoke(navigationRouter) }
    val weights = remember(key1 = childScreens) { calculateWeights(childScreens) }

    Row(modifier = Modifier.fillMaxSize()) {
      for ((index, childScreen) in childScreens.withIndex()) {
        val weight = weights[index]

        Box(
          modifier = Modifier
            .fillMaxHeight()
            .weight(weight = weight)
        ) {
          childScreen.composeScreen.Content()

          if (index >= 0 && index < childScreens.size) {
            Divider(
              modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(chanTheme.dividerColorCompose)
            )
          }
        }
      }
    }
  }

  private fun calculateWeights(childScreens: List<ChildScreen>): FloatArray {
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

  class ChildScreen(
    val composeScreen: ComposeScreenWithToolbar,
    val weight: Float? = null
  )

  companion object {
    val SCREEN_KEY = ScreenKey("SplitScreenLayout")
  }
}