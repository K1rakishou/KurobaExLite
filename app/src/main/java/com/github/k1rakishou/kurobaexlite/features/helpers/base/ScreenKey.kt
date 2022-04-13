package com.github.k1rakishou.kurobaexlite.features.helpers.base

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class ScreenKey(val key: String) : Parcelable