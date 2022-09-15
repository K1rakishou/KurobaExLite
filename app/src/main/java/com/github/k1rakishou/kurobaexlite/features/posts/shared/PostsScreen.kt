package com.github.k1rakishou.kurobaexlite.features.posts.shared

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupPostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostsState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenLocalSearchToolbar
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.managers.Captcha
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.CancellationException

abstract class PostsScreen<ToolbarType : KurobaChildToolbar>(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<ToolbarType>(screenArgs, componentActivity, navigationRouter) {
  abstract val isCatalogScreen: Boolean

  override val dragToCloseEnabledState: MutableState<Boolean> = mutableStateOf(false)
  override val hasFab: Boolean = true

  protected fun showRepliesForPost(popupPostViewMode: PopupPostsScreen.PopupPostViewMode) {
    val popupPostsScreen = ComposeScreen.createScreen<PopupPostsScreen>(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      args = { putParcelable(PopupPostsScreen.REPLY_VIEW_MODE, popupPostViewMode) }
    )

    navigationRouter.presentScreen(popupPostsScreen)
  }

  protected fun showFoundPostsInPopup(
    chanDescriptor: ChanDescriptor,
    foundPostDescriptors: List<PostDescriptor>
  ) {
    if (foundPostDescriptors.isEmpty()) {
      return
    }

    val popupPostViewMode = PopupPostsScreen.PopupPostViewMode.PostList(
      chanDescriptor = chanDescriptor,
      postNoWithSubNoList = foundPostDescriptors.map { postDescriptor ->
        postDescriptor.postNo to postDescriptor.postSubNo
      }
    )

    val popupPostsScreen = ComposeScreen.createScreen<PopupPostsScreen>(
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      args = { putParcelable(PopupPostsScreen.REPLY_VIEW_MODE, popupPostViewMode) }
    )

    navigationRouter.presentScreen(popupPostsScreen)
  }

  companion object {
    private const val TAG = "PostsScreen"
  }

}

@Composable
fun ProcessCaptchaRequestEvents(
  currentChanDescriptorProvider: () -> ChanDescriptor?,
  componentActivityProvider: () -> ComponentActivity,
  navigationRouterProvider: () -> NavigationRouter
) {
  val homeScreenViewModel: HomeScreenViewModel = koinRememberViewModel()

  LaunchedEffect(
    key1 = Unit,
    block = {
      homeScreenViewModel.captchaRequestsFlow.collect { (captchaRequest, siteCaptcha) ->
        if (captchaRequest.chanDescriptor != currentChanDescriptorProvider()) {
          return@collect
        }

        val componentActivity = componentActivityProvider()
        val navigationRouter = navigationRouterProvider()

        val captchaScreen = when (siteCaptcha) {
          SiteCaptcha.Chan4Captcha -> {
            ComposeScreen.createScreen<Chan4CaptchaScreen>(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              args = {
                putParcelable(Chan4CaptchaScreen.CHAN_DESCRIPTOR_ARG, captchaRequest.chanDescriptor)
              },
              callbacks = {
                callback<Captcha>(
                  callbackKey = Chan4CaptchaScreen.ON_CAPTCHA_SOLVED,
                  func = { captcha ->
                    if (captchaRequest.completableDeferred.isActive) {
                      captchaRequest.completableDeferred.complete(captcha)
                    }
                  }
                )

                callback(
                  callbackKey = Chan4CaptchaScreen.ON_SCREEN_DISMISSED,
                  func = {
                    if (captchaRequest.completableDeferred.isActive) {
                      captchaRequest.completableDeferred.completeExceptionally(CancellationException())
                    }
                  }
                )
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
fun <ToolbarType : KurobaChildToolbar> PostListSearchButtons(
  postsScreenViewModelProvider: () -> PostScreenViewModel,
  searchToolbarProvider: () -> PostsScreenLocalSearchToolbar,
  kurobaToolbarContainerStateProvider: () -> KurobaToolbarContainerState<ToolbarType>
) {
  val postsScreenViewModel = postsScreenViewModelProvider()
  val searchToolbar = searchToolbarProvider()

  val postsAsyncData by postsScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()
  val searchQueryMut by postsScreenViewModel.postScreenState.searchQueryFlow.collectAsState()
  val foundEntries by searchToolbar.foundEntries

  val searchQuery = searchQueryMut

  val postsState = if (postsAsyncData !is AsyncData.Data) {
    return
  } else {
    (postsAsyncData as AsyncData.Data).data
  }

  LaunchedEffect(
    key1 = postsAsyncData,
    block = {
      postsState.searchQueryUpdatedFlow.collect {
        val kurobaToolbarContainerState = kurobaToolbarContainerStateProvider()

        if (!kurobaToolbarContainerState.contains(searchToolbar.toolbarKey)) {
          return@collect
        }

        searchToolbar.onSearchUpdated(postsState.postsMatchedBySearchQuery)
      }
    }
  )

  if (searchQuery.isNullOrEmpty() || foundEntries.isEmpty()) {
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
      IntOffset(
        x = -24.dp.roundToPx(),
        y = -(32.dp.roundToPx() + windowInsets.bottom.roundToPx())
      )
    }
  }

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    val bgColor = Color.White

    val paddingBetweenButtons = 8.dp
    val width = 48.dp
    val height = 72.dp + paddingBetweenButtons

    KurobaComposeCard(
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
                val searchToolbar = searchToolbarProvider()

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
                val searchToolbar = searchToolbarProvider()

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