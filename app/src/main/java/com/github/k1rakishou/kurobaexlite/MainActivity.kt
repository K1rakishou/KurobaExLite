package com.github.k1rakishou.kurobaexlite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import com.github.k1rakishou.kurobaexlite.ui.screens.MainScreen
import com.github.k1rakishou.kurobaexlite.ui.theme.KurobaExLiteTheme

class MainActivity : ComponentActivity() {
  private val viewModel by viewModels<MainActivityViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      KurobaExLiteTheme {
        val mainScreen = remember { MainScreen(this, viewModel.rootNavigationRouter) }
        mainScreen.Content()
      }
    }

    if (intent != null) {
      onNewIntent(intent)
    }
  }

  override fun onNewIntent(intent: Intent?) {
    if (intent != null) {
      viewModel.rootNavigationRouter.onNewIntent(intent)
    }
  }
}