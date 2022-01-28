package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.KurobaToolbarLayout(
  leftIcon: @Composable BoxScope.() -> Unit,
  middlePart: @Composable BoxScope.() -> Unit,
  rightIcons: @Composable BoxScope.() -> Unit
) {
  Row(
    modifier = Modifier
      .wrapContentSize()
      .align(Alignment.CenterStart)
  ) {
    Spacer(modifier = Modifier.width(8.dp))

    Box(modifier = Modifier.size(24.dp)) {
      leftIcon()
    }

    Spacer(modifier = Modifier.width(4.dp))

    Box(modifier = Modifier.weight(1f)) {
      middlePart()
    }

    Spacer(modifier = Modifier.width(4.dp))

    Box(modifier = Modifier.wrapContentWidth()) {
      rightIcons()
    }

    Spacer(modifier = Modifier.width(8.dp))
  }
}