package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeNavigationScreen

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean,
) : HomeNavigationScreen(componentActivity, navigationRouter, isStartScreen) {

  abstract val isCatalogScreen: Boolean

}