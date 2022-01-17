package com.github.k1rakishou.kurobaexlite.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun EmptyScreen() {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color.DarkGray
  ) {
    Text(text = "Empty screen")
  }
}