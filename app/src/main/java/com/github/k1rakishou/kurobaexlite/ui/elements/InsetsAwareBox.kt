package com.github.k1rakishou.kurobaexlite.ui.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets

@Composable
fun InsetsAwareBox(
  modifier: Modifier = Modifier,
  contentAlignment: Alignment = Alignment.TopStart,
  applyTop: Boolean = true,
  applyBottom: Boolean = true,
  additionalPaddings: PaddingValues = remember { PaddingValues() },
  content: @Composable () -> Unit
) {
  val insets = LocalWindowInsets.current

  Box(
    modifier = modifier.then(
      Modifier
        .padding(
          top = insets.top.takeIf { applyTop }
            ?.plus(additionalPaddings.calculateTopPadding())
            ?: 0.dp,
          bottom = insets.bottom.takeIf { applyBottom }
            ?.plus(additionalPaddings.calculateBottomPadding())
            ?: 0.dp
        )
    ),
    contentAlignment = contentAlignment
  ) {
    content()
  }
}