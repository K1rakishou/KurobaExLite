package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon

class KurobaToolbarIcon<T : Any>(
  val key: T,
  @DrawableRes drawableId: Int,
  visible: Boolean = true,
  enabled: Boolean = true
) {
  val visible = mutableStateOf(visible)
  val drawableId = mutableStateOf(drawableId)
  val enabled = mutableStateOf(enabled)

  @Composable
  fun Content(onClick: (T) -> Unit) {
    val iconVisible by visible
    if (!iconVisible) {
      return
    }

    val iconDrawableId by drawableId
    val enabled by enabled

    KurobaComposeClickableIcon(
      modifier = Modifier
        .size(KurobaChildToolbar.toolbarIconSize)
        .padding(KurobaChildToolbar.toolbarIconPadding),
      drawableId = iconDrawableId,
      enabled = enabled,
      onClick = { onClick(key) }
    )
  }
}