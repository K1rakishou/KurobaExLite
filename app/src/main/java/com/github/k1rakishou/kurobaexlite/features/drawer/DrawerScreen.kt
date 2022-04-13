package com.github.k1rakishou.kurobaexlite.features.drawer

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.features.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.features.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks

class DrawerScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current

    val paddings = remember(key1 = insets.top, key2 = insets.bottom) {
      PaddingValues(top = insets.top, bottom = insets.bottom)
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose)
        .consumeClicks(consume = true)
        .padding(paddings)
    ) {
      KurobaComposeText(text = "App: ${BuildConfig.FLAVOR}-${BuildConfig.BUILD_TYPE}-${BuildConfig.VERSION_NAME}-${Build.SUPPORTED_ABIS.first()}")
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("DrawerScreen")
  }
}