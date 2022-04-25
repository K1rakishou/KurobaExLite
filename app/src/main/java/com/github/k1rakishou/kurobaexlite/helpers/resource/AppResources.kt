package com.github.k1rakishou.kurobaexlite.helpers.resource

import androidx.annotation.StringRes

interface AppResources {
  fun string(@StringRes stringId: Int, vararg args: Any): String
}