package com.github.k1rakishou.kurobaexlite.features.posts.shared

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.github.k1rakishou.kurobaexlite.features.captcha.Chan4CaptchaScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import kotlinx.coroutines.CancellationException

abstract class PostsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {
  abstract val isCatalogScreen: Boolean

  override val hasFab: Boolean = true

  protected fun showRepliesForPost(replyViewMode: PopupRepliesScreen.ReplyViewMode) {
    navigationRouter.presentScreen(
      PopupRepliesScreen(
        replyViewMode = replyViewMode,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter
      )
    )
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