package com.github.k1rakishou.kurobaexlite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers.setupEdgeToEdge
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers.setupStatusAndNavBarColors
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.ProvideChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.ProvideKurobaViewConfiguration
import com.github.k1rakishou.kurobaexlite.ui.helpers.ProvideWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.MainScreen
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
  private val viewModel by viewModels<MainActivityViewModel>()
  private val themeEngine by inject<ThemeEngine>(ThemeEngine::class.java)
  private val uiInfoManager by inject<UiInfoManager>(UiInfoManager::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    window.setupEdgeToEdge()
    window.setupStatusAndNavBarColors(theme = themeEngine.chanTheme)

    setContent {
      ProvideKurobaViewConfiguration {
        ProvideWindowInsets(window = window) {
          ProvideChanTheme(themeEngine = themeEngine) {
            val mainScreen = remember { MainScreen(this, viewModel.rootNavigationRouter) }
            mainScreen.Content()
          }
        }
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