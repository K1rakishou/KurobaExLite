package com.github.k1rakishou.kurobaexlite.features.posts.shared

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.captcha.Chan4CaptchaScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenLocalSearchToolbar
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest

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

  @Composable
  protected fun PostListSearchButtons(
    postsScreenViewModel: PostScreenViewModel,
    searchToolbar: PostsScreenLocalSearchToolbar
  ) {
    val postsAsyncData by postsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()

    val searchQueryMut by postsScreenViewModel.postScreenState.searchQueryFlow.collectAsState()
    val searchQuery = searchQueryMut

    val postsState = if (postsAsyncData !is AsyncData.Data) {
      return
    } else {
      (postsAsyncData as AsyncData.Data).data
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        postsState.searchQueryUpdatedFlow.collectLatest {
          if (!kurobaToolbarContainerState.contains(searchToolbar.toolbarKey)) {
            return@collectLatest
          }

          searchToolbar.onSearchUpdated(postsState.postsMatchedBySearchQuery)
        }
      }
    )

    if (searchQuery.isNullOrEmpty()) {
      return
    }

    LaunchedEffect(
      key1 = searchQuery,
      block = {
        if (searchQuery.length < PostsState.MIN_SEARCH_QUERY_LENGTH) {
          return@LaunchedEffect
        }

        val firstPostDescriptor = searchToolbar.firstEntry()
          ?: return@LaunchedEffect

        postsScreenViewModel.scrollToPost(firstPostDescriptor)
      }
    )

    val windowInsets = LocalWindowInsets.current
    val density = LocalDensity.current

    val offset = with(density) {
      remember(key1 = windowInsets) {
        IntOffset(x = -24.dp.roundToPx(), y = -(32.dp.roundToPx() + windowInsets.bottom.roundToPx()))
      }
    }

    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      val bgColor = Color.White

      val paddingBetweenButtons = 8.dp
      val width = 48.dp
      val height = 72.dp + paddingBetweenButtons

      Card(
        modifier = Modifier
          .width(width)
          .height(height)
          .align(Alignment.BottomEnd)
          .absoluteOffset { offset },
        backgroundColor = bgColor
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          KurobaComposeIcon(
            modifier = Modifier
              .fillMaxWidth()
              .height((height - paddingBetweenButtons) / 2)
              .kurobaClickable(
                onClick = {
                  val prevPostDescriptor = searchToolbar.prevEntry()
                    ?: return@kurobaClickable

                  postsScreenViewModel.scrollToPost(prevPostDescriptor)
                }
              ),
            drawableId = R.drawable.ic_baseline_keyboard_arrow_up_24,
            iconColor = Color.Black
          )

          Spacer(modifier = Modifier.height(paddingBetweenButtons))

          KurobaComposeIcon(
            modifier = Modifier
              .fillMaxWidth()
              .height((height - paddingBetweenButtons) / 2)
              .kurobaClickable(
                onClick = {
                  val nextPostDescriptor = searchToolbar.nextEntry()
                    ?: return@kurobaClickable

                  postsScreenViewModel.scrollToPost(nextPostDescriptor)
                }
              ),
            drawableId = R.drawable.ic_baseline_keyboard_arrow_down_24,
            iconColor = Color.Black
          )
        }
      }
    }
  }

}