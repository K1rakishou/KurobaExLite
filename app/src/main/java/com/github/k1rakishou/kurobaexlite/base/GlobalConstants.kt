package com.github.k1rakishou.kurobaexlite.base

import android.content.Context
import android.content.res.Configuration
import com.github.k1rakishou.kurobaexlite.R

class GlobalConstants(
  private val appContext: Context
) {
  private val resources by lazy { appContext.resources }

  val coresCount by lazy { Runtime.getRuntime().availableProcessors() }
  val isTablet by lazy { resources.getBoolean(R.bool.isTablet) }

  fun mainUiLayoutMode(): MainUiLayoutMode {
    val orientation = appContext.resources.configuration.orientation

    if (isTablet) {
      return MainUiLayoutMode.TwoWaySplit
    }

    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return MainUiLayoutMode.Portrait
    }

    return MainUiLayoutMode.TwoWaySplit
  }

}

enum class MainUiLayoutMode(val isSplit: Boolean) {
  Portrait(false),
  TwoWaySplit(true)
}