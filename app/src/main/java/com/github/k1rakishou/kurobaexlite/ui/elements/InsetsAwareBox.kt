package com.github.k1rakishou.kurobaexlite.ui.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets

@Composable
fun InsetsAwareBox(
  modifier: Modifier = Modifier,
  applyLeft: Boolean = true,
  applyRight: Boolean = true,
  applyTop: Boolean = true,
  applyBottom: Boolean = true,
  content: @Composable () -> Unit
) {
  val insets = LocalWindowInsets.current

  Box(
    modifier = modifier.then(
      Modifier
        .padding(
          start = insets.left.takeIf { applyLeft } ?: 0.dp,
          end = insets.right.takeIf { applyRight } ?: 0.dp,
          top = insets.top.takeIf { applyTop } ?: 0.dp,
          bottom = insets.bottom.takeIf { applyBottom } ?: 0.dp
        )
    )
  ) {
    content()
  }
}