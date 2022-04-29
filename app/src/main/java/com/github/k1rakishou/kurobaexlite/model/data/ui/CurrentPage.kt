package com.github.k1rakishou.kurobaexlite.model.data.ui

import android.os.Parcelable
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.parcelize.Parcelize

@Parcelize
data class CurrentPage(
  val screenKey: ScreenKey,
  val animate: Boolean = false
) : Parcelable