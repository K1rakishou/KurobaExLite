package com.github.k1rakishou.kurobaexlite.features.home.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

abstract class AbstractPage<T : ComposeScreen> {
  abstract val childScreens: List<ChildScreen<T>>

  abstract fun screenKey(): ScreenKey
  abstract fun hasScreen(screenKey: ScreenKey): Boolean
  abstract fun screenHasChildren(screenKey: ScreenKey): Boolean
  abstract fun canDragPager(): Boolean

  class ChildScreen<T : ComposeScreen>(
    val composeScreen: T,
    val weight: Float? = null
  ) {
    val screenKey: ScreenKey
      get() = composeScreen.screenKey

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as ChildScreen<*>

      if (composeScreen != other.composeScreen) return false

      return true
    }

    override fun hashCode(): Int {
      return composeScreen.hashCode()
    }

  }

  @Composable
  abstract fun Toolbar(boxScope: BoxScope)

  @Composable
  abstract fun Content()

  @Composable
  protected fun DisplayTopToolbarsWithTransition(
    composeScreen: ComposeScreenWithToolbar<*>,
    boxScope: BoxScope
  ) {
    val lastTwoChildScreens by remember(key1 = composeScreen.screenKey) {
      derivedStateOf { composeScreen.lastTwoChildScreens() }
    }

    val topScreen = remember(key1 = lastTwoChildScreens) {
      lastTwoChildScreens.lastOrNull() as? HomeNavigationScreen<*>
    }

    if (topScreen == null) {
      return
    }

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val toolbarTranslationDistancePx = with(LocalDensity.current) { toolbarHeight.toPx() / 3f }
    val animationProgress by topScreen.animationProgress

    for ((childScreenIndex, childScreen) in lastTwoChildScreens.withIndex()) {
      key(childScreen.screenKey) {
        val screenContentMovable = remember(key1 = childScreen.screenKey) {
          movableContentOf { childScreen.Toolbar(boxScope) }
        }

        val toolbarAlpha = if (childScreenIndex == 0) {
          lerpFloat(1f, 0f, Math.abs(animationProgress))
        } else {
          lerpFloat(0f, 1f, Math.abs(animationProgress))
        }

        val toolbarTranslation = if (childScreenIndex == 0) {
          if (animationProgress >= 0f) {
            lerpFloat(0f, toolbarTranslationDistancePx, Math.abs(animationProgress))
          } else {
            lerpFloat(0f, -toolbarTranslationDistancePx, Math.abs(animationProgress))
          }
        } else {
          if (animationProgress >= 0f) {
            lerpFloat(-toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
          } else {
            lerpFloat(toolbarTranslationDistancePx, 0f, Math.abs(animationProgress))
          }
        }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              alpha = toolbarAlpha
              translationY = toolbarTranslation
            }
        ) {
          screenContentMovable()
        }
      }
    }
  }

}