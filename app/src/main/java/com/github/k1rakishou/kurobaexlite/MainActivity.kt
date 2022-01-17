package com.github.k1rakishou.kurobaexlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.github.k1rakishou.kurobaexlite.ui.screens.MainScreen
import com.github.k1rakishou.kurobaexlite.ui.theme.KurobaExLiteTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      KurobaExLiteTheme {
        val mainScreen = remember { MainScreen(this) }
        mainScreen.Content()
      }
    }
  }

}