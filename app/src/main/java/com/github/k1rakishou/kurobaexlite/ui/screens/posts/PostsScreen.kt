package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply.PopupRepliesScreen

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  isStartScreen: Boolean,
) : HomeNavigationScreen(componentActivity, navigationRouter, isStartScreen) {

  abstract val isCatalogScreen: Boolean

  protected fun showRepliesForPost(postDescriptor: PostDescriptor) {
    navigationRouter.presentScreen(
      PopupRepliesScreen(
        postDescriptor = postDescriptor,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter
      )
    )
  }

}