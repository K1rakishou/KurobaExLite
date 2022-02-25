package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply.PopupRepliesScreen

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {

  abstract val isCatalogScreen: Boolean

  protected fun showRepliesForPost(replyViewMode: PopupRepliesScreen.ReplyViewMode) {
    navigationRouter.presentScreen(
      PopupRepliesScreen(
        replyViewMode = replyViewMode,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter
      )
    )
  }

}