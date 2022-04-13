package com.github.k1rakishou.kurobaexlite.model.data.ui

import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

data class CurrentPage(
  val screenKey: ScreenKey,
  val animate: Boolean = false
)