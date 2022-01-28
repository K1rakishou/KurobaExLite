package com.github.k1rakishou.kurobaexlite.ui.screens.helpers

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter

abstract class HomeNavigationScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  val isStartScreen: Boolean
) : ComposeScreenWithToolbar(componentActivity, navigationRouter)