package com.github.k1rakishou.kurobaexlite.features.screenshot

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.RuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalRuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.verticalScrollbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PostScreenshotScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : FloatingComposeScreen(
  screenArgs = screenArgs,
  componentActivity = componentActivity,
  navigationRouter = navigationRouter,
  canDismissByClickingOutside = false
) {
  override val screenKey: ScreenKey = SCREEN_KEY

  private val postScreenshotScreenViewModel by componentActivity.viewModel<PostScreenshotScreenViewModel>()
  private val catalogScreenViewModel by componentActivity.viewModel<CatalogScreenViewModel>()
  private val threadScreenViewModel by componentActivity.viewModel<ThreadScreenViewModel>()

  private val chanDescriptor by argumentOrNullLazy<ChanDescriptor>(CHAN_DESCRIPTOR)
  private val postDescriptors by argumentOrDefaultLazy<Array<PostDescriptor>>(POST_DESCRIPTORS, emptyArray())

  @Composable
  override fun FloatingContent() {
    super.FloatingContent()

    val chanDescriptorLocal = chanDescriptor
    if (postDescriptors.isEmpty() || chanDescriptorLocal == null) {
      LaunchedEffect(
        key1 = Unit,
        block = {
          delay(200)
          stopPresenting()
        }
      )

      return
    }

    val context = LocalContext.current
    val runtimePermissionsHelper = LocalRuntimePermissionsHelper.current
    val chanTheme = LocalChanTheme.current

    val postScreenshot = remember { PostScreenshot() }
    val coroutineScope = rememberCoroutineScope()

    val postListOptionsMut by produceState<PostListOptions?>(
      initialValue = null,
      producer = {
        val postCellCommentTextSizeSp = appSettings.postCellCommentTextSizeSp.read()
        val postCellSubjectTextSizeSp = appSettings.postCellSubjectTextSizeSp.read()
        val catalogPostViewMode = if (chanDescriptor is CatalogDescriptor) {
          appSettings.catalogPostViewMode.read().toPostViewMode()
        } else {
          PostViewMode.List
        }

        value = PostListOptions(
          isCatalogMode = false,
          showThreadStatusCell = false,
          textSelectionEnabled = false,
          isInPopup = false,
          openedFromScreenKey = screenKey,
          pullToRefreshEnabled = false,
          detectLinkableClicks = false,
          mainUiLayoutMode = MainUiLayoutMode.Phone,
          contentPadding = PaddingValues(0.dp),
          postCellCommentTextSizeSp = postCellCommentTextSizeSp.sp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp.sp,
          orientation = Configuration.ORIENTATION_PORTRAIT,
          postViewMode = catalogPostViewMode
        )
      }
    )

    val postListOptions = postListOptionsMut
    if (postListOptions == null) {
      return
    }

    var screenshotJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        val postCellDataList = when (chanDescriptorLocal) {
          is CatalogDescriptor -> catalogScreenViewModel.postScreenState.getPosts(postDescriptors.toList())
          is ThreadDescriptor -> threadScreenViewModel.postScreenState.getPosts(postDescriptors.toList())
        }

        if (postCellDataList.isEmpty()) {
          stopPresenting()
          return@LaunchedEffect
        }

        postScreenshot.init(chanTheme, postCellDataList)
      }
    )

    val scrollState = rememberScrollState()

    Column {
      val screenshotInProgress = screenshotJob != null
      val contentPadding = PaddingValues()

      Column(
        modifier = Modifier
          .verticalScroll(
            state = scrollState,
            enabled = !screenshotInProgress
          )
          .verticalScrollbar(
            contentPadding = contentPadding,
            scrollState = scrollState,
            enabled = screenshotJob == null
          )
          .weight(1f, false)
      ) {
        postScreenshot.ScreenshotContainer(
          chanDescriptor = chanDescriptorLocal,
          postListOptions = postListOptions
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row {
        Spacer(modifier = Modifier.weight(1f))

        val buttonTextId = if (screenshotJob == null) {
          R.string.close
        } else {
          R.string.cancel
        }

        KurobaComposeTextBarButton(
          text = stringResource(id = buttonTextId),
          onClick = {
            if (screenshotJob == null) {
              stopPresenting()
            } else {
              screenshotJob?.cancel()
              screenshotJob = null
            }
          }
        )

        Spacer(modifier = Modifier.width(16.dp))

        KurobaComposeTextBarButton(
          text = stringResource(id = R.string.post_screenshot_screen_take_screenshot),
          enabled = screenshotJob == null,
          onClick = {
            val appContext = context.applicationContext
            val composableHeight = postScreenshot.composableBounds.value?.height
              ?: return@KurobaComposeTextBarButton

            screenshotJob = coroutineScope.launch {
              try {
                scrollState.scrollTo(0)
                awaitFrame()

                onTakeScreenshotButtonClicked(
                  runtimePermissionsHelper = runtimePermissionsHelper,
                  appContext = appContext,
                  postScreenshot = postScreenshot,
                  scrollContent = {
                    if (!scrollState.canScrollForward) {
                      return@onTakeScreenshotButtonClicked -1
                    }

                    val toScroll = composableHeight.coerceAtMost((scrollState.maxValue - scrollState.value).toFloat())
                    var scrolled = -1f

                    scrollState.scroll(MutatePriority.PreventUserInput) { scrolled = scrollBy(toScroll) }
                    awaitFrame()

                    return@onTakeScreenshotButtonClicked scrolled.toInt()
                  }
                )
              } finally {
                screenshotJob = null
              }
            }
          }
        )

        Spacer(modifier = Modifier.width(12.dp))
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  private suspend fun onTakeScreenshotButtonClicked(
    runtimePermissionsHelper: RuntimePermissionsHelper,
    appContext: Context,
    postScreenshot: PostScreenshot,
    scrollContent: suspend () -> Int
  ) {
    val granted = runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    if (!granted) {
      snackbarManager.errorToast(R.string.post_screenshot_screen_need_permissions)
      return
    }

    val result = postScreenshotScreenViewModel.performScreenshot(
      appContext = appContext,
      postScreenshot = postScreenshot,
      scrollContent = scrollContent
    )

    if (result.isFailure) {
      val errorMessage = result.exceptionOrThrow().errorMessageOrClassName(userReadable = true)
      snackbarManager.errorToast(appResources.string(R.string.post_screenshot_screen_take_screenshot_error, errorMessage))
      return
    }

    val success = result.getOrThrow()
    if (!success) {
      snackbarManager.errorToast(R.string.post_screenshot_screen_take_screenshot_failure)
      return
    }

    snackbarManager.toast(R.string.post_screenshot_screen_take_screenshot_success)
    stopPresenting()
  }

  companion object {
    val SCREEN_KEY = ScreenKey("PostScreenshotScreen")

    const val POST_DESCRIPTORS = "post_descriptors"
    const val CHAN_DESCRIPTOR = "chan_descriptor"
  }
}