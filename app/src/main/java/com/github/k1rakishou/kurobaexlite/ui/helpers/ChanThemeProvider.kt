package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine

val LocalChanTheme = staticCompositionLocalOf<ChanTheme> { error("Theme not provided") }

@Composable
fun ProvideChanTheme(
  themeEngine: ThemeEngine,
  content: @Composable () -> Unit
) {
  var chanTheme by remember { mutableStateOf(themeEngine.chanTheme) }

  DisposableEffect(themeEngine.chanTheme) {
    val themeUpdateObserver = object : ThemeEngine.ThemeChangesListener {
      override fun onThemeChanged() {
        chanTheme = themeEngine.chanTheme.fullCopy()
      }
    }

    themeEngine.addListener(themeUpdateObserver)
    onDispose { themeEngine.removeListener(themeUpdateObserver) }
  }

  CompositionLocalProvider(LocalChanTheme provides chanTheme) {
    val originalColors = MaterialTheme.colors

    val updatedColors = remember(key1 = themeEngine.chanTheme) {
      originalColors.copy(
        primary = chanTheme.primaryColorCompose,
        error = chanTheme.errorColorCompose
      )
    }

    MaterialTheme(colors = updatedColors) {
      content()
    }
  }
}