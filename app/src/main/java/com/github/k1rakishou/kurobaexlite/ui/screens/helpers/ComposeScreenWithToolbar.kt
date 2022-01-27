package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

abstract class ComposeScreenWithToolbar(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {

  @Composable
  abstract fun Toolbar(boxScope: BoxScope)

}