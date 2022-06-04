package com.github.k1rakishou.kurobaexlite.features.home.pages

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
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

}