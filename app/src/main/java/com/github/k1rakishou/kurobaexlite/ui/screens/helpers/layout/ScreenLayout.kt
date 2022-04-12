package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.layout

import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey

interface ScreenLayout {
  fun hasScreen(screenKey: ScreenKey): Boolean
}