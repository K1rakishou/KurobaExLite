package com.github.k1rakishou.kurobaexlite.ui.screens.home

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreenWithToolbar
import kotlinx.coroutines.flow.StateFlow

abstract class HomeNavigationScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreenWithToolbar(componentActivity, navigationRouter) {
  open val hasFab: Boolean = true

  abstract val screenContentLoadedFlow: StateFlow<Boolean>
}