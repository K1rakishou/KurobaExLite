package com.github.k1rakishou.kurobaexlite.helpers.resource

import androidx.annotation.BoolRes
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Density

interface AppResources {
  val composeDensity: Density

  fun string(@StringRes stringId: Int, vararg args: Any): String
  fun dimension(@DimenRes dimenId: Int): Float
  fun boolean(@BoolRes boolRes: Int): Boolean
}