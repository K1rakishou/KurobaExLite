package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey

class FloatingMenuScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val menuItems: List<ToolbarMenuItem>
) : FloatingComposeScreen(componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun BoxScope.FloatingContent() {
    KurobaComposeText(text = "FloatingMenuScreen")
  }

  companion object {
    private val SCREEN_KEY = ScreenKey("FloatingMenuScreen")
  }
}