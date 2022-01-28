package com.github.k1rakishou.kurobaexlite.base

import android.content.Context

class GlobalConstants(
  private val appContext: Context
) {
  val coresCount by lazy { Runtime.getRuntime().availableProcessors() }
}

