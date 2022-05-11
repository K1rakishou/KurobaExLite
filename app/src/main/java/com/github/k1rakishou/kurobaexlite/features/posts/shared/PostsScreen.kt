package com.github.k1rakishou.kurobaexlite.features.posts.shared

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.github.k1rakishou.kurobaexlite.features.captcha.Chan4CaptchaScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.ui.helpers.layout.SplitScreenLayout
import kotlinx.coroutines.CancellationException

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

  protected fun canProcessBackEvent(
    uiLayoutMode: MainUiLayoutMode,
    currentPage: CurrentPage?
  ): Boolean {
    return when (uiLayoutMode) {
      MainUiLayoutMode.Phone -> {
        currentPage?.screenKey == screenKey
      }
      MainUiLayoutMode.Split -> {
        currentPage?.screenKey == screenKey || currentPage?.screenKey == SplitScreenLayout.SCREEN_KEY
      }
    }
  }

  @Composable
  protected fun ProcessCaptchaRequestEvents(
    homeScreenViewModel: HomeScreenViewModel,
    currentChanDescriptor: () -> ChanDescriptor?
  ) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        homeScreenViewModel.captchaRequestsFlow.collect { (captchaRequest, siteCaptcha) ->
          if (captchaRequest.chanDescriptor != currentChanDescriptor()) {
            return@collect
          }

          val captchaScreen = when (siteCaptcha) {
            SiteCaptcha.Chan4Captcha -> {
              Chan4CaptchaScreen(
                componentActivity = componentActivity,
                navigationRouter = navigationRouter,
                chanDescriptor = captchaRequest.chanDescriptor,
                onCaptchaSolved = { captcha ->
                  if (captchaRequest.completableDeferred.isActive) {
                    captchaRequest.completableDeferred.complete(captcha)
                  }
                },
                onScreenDismissed = {
                  if (captchaRequest.completableDeferred.isActive) {
                    captchaRequest.completableDeferred.completeExceptionally(CancellationException())
                  }
                }
              )
            }
          }

          navigationRouter.presentScreen(captchaScreen)
        }
      }
    )
  }

}