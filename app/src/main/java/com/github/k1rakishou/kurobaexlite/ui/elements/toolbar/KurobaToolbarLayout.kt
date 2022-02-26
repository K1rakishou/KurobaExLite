package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.KurobaToolbarLayout(
  leftPart: (@Composable BoxScope.() -> Unit)? = null,
  middlePart: @Composable BoxScope.() -> Unit,
  rightPart: @Composable BoxScope.() -> Unit
) {
  Row(
    modifier = Modifier
      .wrapContentSize()
  ) {
    Spacer(modifier = Modifier.width(8.dp))

    if (leftPart != null) {
      Box(
        modifier = Modifier.fillMaxHeight().width(24.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        leftPart()
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    Box(
      modifier = Modifier.fillMaxHeight().weight(1f),
      contentAlignment = Alignment.CenterStart
    ) {
      middlePart()
    }

    Spacer(modifier = Modifier.width(8.dp))

    Box(
      modifier = Modifier.fillMaxHeight().wrapContentWidth(),
      contentAlignment = Alignment.CenterStart
    ) {
      rightPart()
    }

    Spacer(modifier = Modifier.width(8.dp))
  }
}