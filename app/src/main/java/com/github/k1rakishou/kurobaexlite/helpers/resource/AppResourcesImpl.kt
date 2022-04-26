package com.github.k1rakishou.kurobaexlite.helpers.resource

import android.content.Context

class AppResourcesImpl(
  private val appContext: Context
) : AppResources {

  override fun string(stringId: Int, vararg args: Any): String {
    return if (args.isEmpty()) {
      appContext.resources.getString(stringId)
    } else {
      appContext.resources.getString(stringId, *args)
    }
  }

}