package com.github.k1rakishou.kurobaexlite.features.home.pages

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
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

class SplitPage private constructor(
  override val childScreens: List<ChildScreen<ComposeScreenWithToolbar<*>>>
) : AbstractPage<ComposeScreenWithToolbar<*>>() {
  private val childScreenKeys by lazy { childScreens.map { it.screenKey } }

  override fun screenKey(): ScreenKey = SCREEN_KEY

  override fun hasScreen(screenKey: ScreenKey): Boolean {
    return childScreens
      .any { childScreen -> childScreen.composeScreen.screenKey == screenKey }
  }

  override fun screenHasChildren(screenKey: ScreenKey): Boolean {
    val childScreen = childScreens
      .firstOrNull { childScreen -> childScreen.composeScreen.screenKey == screenKey }
      ?.composeScreen
      ?: return false

    return childScreen.hasChildScreens()
  }

  override fun canDragPager(): Boolean {
    return childScreens.none { childScreen -> childScreen.composeScreen.hasChildScreens() }
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    val chanTheme = LocalChanTheme.current
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
            val composeScreen = childScreen.composeScreen
            DisplayTopToolbarsWithTransition(composeScreen, boxScope)

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
    val weights = remember(key1 = childScreens) { calculateWeights(childScreens) }

    Row(modifier = Modifier.fillMaxSize()) {
      for ((index, childScreen) in childScreens.withIndex()) {
        val weight = weights[index]

        Box(
          modifier = Modifier
            .fillMaxHeight()
            .weight(weight = weight)
        ) {
          RouterHost(
            navigationRouter = childScreen.composeScreen.navigationRouter,
            defaultScreenFunc = { childScreen.composeScreen }
          )

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

  private fun calculateWeights(childScreens: List<ChildScreen<ComposeScreenWithToolbar<*>>>): FloatArray {
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as SplitPage

    if (childScreenKeys.size != other.childScreenKeys.size) return false

    for (index in childScreenKeys.indices) {
      val thisScreenKey = childScreenKeys[index]
      val otherScreenKey = other.childScreenKeys[index]

      if (thisScreenKey != otherScreenKey) {
        return false
      }
    }

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + childScreenKeys.hashCode()
    return result
  }

  companion object {
    val SCREEN_KEY = ScreenKey("SplitPage")

    fun of(vararg composeScreenWithWeight: Pair<ComposeScreenWithToolbar<*>, Float>): SplitPage {
      check(composeScreenWithWeight.isNotEmpty()) { "input is empty" }

      val childScreens = composeScreenWithWeight.map { (screen, weight) -> ChildScreen(screen, weight) }
      return SplitPage(childScreens)
    }
  }

}