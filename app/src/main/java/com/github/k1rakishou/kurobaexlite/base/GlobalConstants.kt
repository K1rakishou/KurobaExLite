package com.github.k1rakishou.kurobaexlite.base

import android.content.Context
import com.github.k1rakishou.kurobaexlite.R

class GlobalConstants(
  private val appContext: Context
) {
  private val resources by lazy { appContext.resources }

  val coresCount by lazy { Runtime.getRuntime().availableProcessors() }
  val isTablet by lazy { resources.getBoolean(R.bool.isTablet) }
}