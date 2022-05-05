package com.github.k1rakishou.kurobaexlite.helpers.resource

import android.content.Context
import androidx.compose.ui.unit.Density

class AppResourcesImpl(
  private val appContext: Context
) : AppResources {
  override val composeDensity by lazy { Density(appContext) }

  override fun string(stringId: Int, vararg args: Any): String {
    return if (args.isEmpty()) {
      appContext.resources.getString(stringId)
    } else {
      appContext.resources.getString(stringId, *args)
    }
  }

  override fun dimension(dimenId: Int): Float {
    return appContext.resources.getDimension(dimenId)
  }

  override fun boolean(boolRes: Int): Boolean {
    return appContext.resources.getBoolean(boolRes)
  }

}