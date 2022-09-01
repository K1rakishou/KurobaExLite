package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable

class KurobaToolbarIcon<T : Any>(
  val key: T,
  @DrawableRes drawableId: Int,
  visible: Boolean = true
) {
  val visible = mutableStateOf(visible)
  val drawableId = mutableStateOf(drawableId)

  @Composable
  fun Content(onClick: (T) -> Unit) {
    val iconVisible by visible
    if (!iconVisible) {
      return
    }

    val iconDrawableId by drawableId

    KurobaComposeIcon(
      modifier = Modifier
        .padding(horizontal = KurobaChildToolbar.toolbarIconPadding)
        .size(KurobaChildToolbar.toolbarIconSize)
        .kurobaClickable(
          bounded = false,
          onClick = { onClick(key) }
        ),
      drawableId = iconDrawableId
    )
  }
}