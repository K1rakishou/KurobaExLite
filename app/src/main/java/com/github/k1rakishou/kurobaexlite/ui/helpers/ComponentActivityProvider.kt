package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalComponentActivity = staticCompositionLocalOf<ComponentActivity> { error("ComponentActivity not provided") }


@Composable
fun ProvideComponentActivity(
  componentActivity: ComponentActivity,
  content: @Composable () -> Unit
) {
  CompositionLocalProvider(LocalComponentActivity provides componentActivity) {
    content()
  }
}