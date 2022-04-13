package com.github.k1rakishou.kurobaexlite.features.helpers.layout

import com.github.k1rakishou.kurobaexlite.features.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.features.helpers.base.ScreenKey

interface ScreenLayout<T : ComposeScreen> {
  val childScreens: List<ChildScreen<T>>

  fun hasScreen(screenKey: ScreenKey): Boolean

  class ChildScreen<T : ComposeScreen>(
    val composeScreen: T,
    val weight: Float? = null
  )
}