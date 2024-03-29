package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KurobaToolbarLayout(
  leftPart: (@Composable BoxScope.() -> Unit)? = null,
  middlePart: @Composable BoxScope.() -> Unit,
  rightPart: (@Composable RowScope.() -> Unit)?
) {
  Row(
    modifier = Modifier
      .wrapContentSize()
  ) {
    Spacer(modifier = Modifier.width(12.dp))

    if (leftPart != null) {
      Box(
        modifier = Modifier.fillMaxHeight().wrapContentWidth(),
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

    if (rightPart != null) {
      Row(
        modifier = Modifier.fillMaxHeight().wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        rightPart()
      }

      Spacer(modifier = Modifier.width(8.dp))
    }
  }
}