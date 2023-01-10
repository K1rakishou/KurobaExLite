package com.github.k1rakishou.kurobaexlite.helpers.resource

import android.content.Context
import androidx.compose.ui.unit.Density

class AppResources(
  private val appContext: Context
) : IAppResources {
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

  override fun getQuantityString(stringId: Int, quantity: Int, vararg formatArgs: Any): String {
    return appContext.resources.getQuantityString(stringId, quantity, *formatArgs)
  }

}