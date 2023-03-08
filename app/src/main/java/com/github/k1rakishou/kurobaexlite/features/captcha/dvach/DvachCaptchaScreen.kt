package com.github.k1rakishou.kurobaexlite.features.captcha.dvach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.Captcha
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.dvach.Dvach
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeMessage
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class DvachCaptchaScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val dvachCaptchaScreenViewModel: DvachCaptchaScreenViewModel by componentActivity.viewModel()
  private val siteManager: SiteManager by inject(SiteManager::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      dvachCaptchaScreenViewModel.cleanup()

      ScreenCallbackStorage.invokeCallback(screenKey, ON_SCREEN_DISMISSED)
    }

    super.onDisposed(screenDisposeEvent)
  }

  @Composable
  override fun FloatingContent() {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .verticalScroll(
          rememberScrollState()
        )
    ) {
      BuildCaptchaWindow()
    }
  }

  @Composable
  private fun BuildCaptchaWindow() {
    LaunchedEffect(
      key1 = Unit,
      block = { dvachCaptchaScreenViewModel.requestCaptcha() }
    )

    BuildCaptchaInput(
      onReloadClick = { dvachCaptchaScreenViewModel.requestCaptcha() },
      onVerifyClick = { captchaId, token ->
        val solution = CaptchaSolution.ChallengeWithSolution(
          challenge = captchaId,
          solution = token
        )

        val captcha = Captcha.newSolvedCaptcha(captchaSolution = solution, ttlMs = TimeUnit.SECONDS.toMillis(90))
        ScreenCallbackStorage.invokeCallback(screenKey, ON_CAPTCHA_SOLVED, captcha)
        stopPresenting()
      }
    )
  }

  @Composable
  private fun BuildCaptchaInput(
    onReloadClick: () -> Unit,
    onVerifyClick: (String, String) -> Unit
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      BuildCaptchaImage(onReloadClick)

      Spacer(modifier = Modifier.height(16.dp))

      var currentInputValue by dvachCaptchaScreenViewModel.currentInputValue
      val captchaInfoAsync by dvachCaptchaScreenViewModel.captchaInfoToShow
      val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

      val captchaId = captchaInfo?.id

      if (captchaInfo != null) {
        val input = captchaInfo.input

        val keyboardOptions = remember(key1 = input) {
          when (input) {
            null -> KeyboardOptions.Default
            "numeric" -> KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.NumberPassword)
            else -> KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)
          }
        }

        KurobaComposeTextField(
          value = currentInputValue,
          onValueChange = { newValue -> currentInputValue = newValue },
          maxLines = 1,
          singleLine = true,
          keyboardOptions = keyboardOptions,
          keyboardActions = KeyboardActions(
            onDone = {
              if (captchaId.isNotNullNorEmpty()) {
                onVerifyClick(captchaId, dvachCaptchaScreenViewModel.currentInputValue.value.text)
              }
            }
          ),
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
      }

      Spacer(modifier = Modifier.weight(1f))

      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        KurobaComposeTextBarButton(
          onClick = onReloadClick,
          text = stringResource(id = R.string.dvach_captcha_layout_reload)
        )

        Spacer(modifier = Modifier.width(8.dp))

        val buttonEnabled = captchaId.isNotNullNorEmpty() && currentInputValue.text.isNotEmpty()

        KurobaComposeTextBarButton(
          onClick = {
            if (captchaId.isNotNullNorEmpty()) {
              onVerifyClick(captchaId, dvachCaptchaScreenViewModel.currentInputValue.value.text)
            }
          },
          enabled = buttonEnabled,
          text = stringResource(id = R.string.dvach_captcha_layout_verify)
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  @Composable
  private fun BuildCaptchaImage(
    onReloadClick: () -> Unit
  ) {
    val context = LocalContext.current

    Box(
      modifier = Modifier
        .height(160.dp)
        .fillMaxWidth()
    ) {
      val captchaInfoAsync by dvachCaptchaScreenViewModel.captchaInfoToShow
      when (captchaInfoAsync) {
        AsyncData.Uninitialized,
        AsyncData.Loading -> {
          // no-op
        }
        is AsyncData.Error -> {
          val error = (captchaInfoAsync as AsyncData.Error).error
          KurobaComposeMessage(
            modifier = Modifier.fillMaxSize(),
            message = error.errorMessageOrClassName(userReadable = true)
          )
        }
        is AsyncData.Data -> {
          val requestFullUrl = remember {
            (captchaInfoAsync as AsyncData.Data).data.fullRequestUrl(siteManager = siteManager)
          }

          if (requestFullUrl == null) {
            return@Box
          }

          val request by produceState<ImageRequest?>(initialValue = null, key1 = requestFullUrl) {
            value = ImageRequest.Builder(context)
              .data(requestFullUrl)
              .size(Size.ORIGINAL)
              .also { imageRequestBuilder ->
                siteManager.bySiteKey(Dvach.SITE_KEY)
                  ?.requestModifier()
                  ?.modifyCoilImageRequest(requestFullUrl, imageRequestBuilder)
              }
              .build()
          }

          if (request != null) {
            SubcomposeAsyncImage(
              modifier = Modifier
                .fillMaxSize()
                .clickable { onReloadClick() },
              model = request,
              contentDescription = "Dvach captcha",
              content = {
                val state = painter.state

                if (state is AsyncImagePainter.State.Error) {
                  LaunchedEffect(
                    key1 = state,
                    block = {
                      logcatError("BuildCaptchaImage") {
                        "Failed to load captcha image (${requestFullUrl}), " +
                          "error: ${state.result.throwable.errorMessageOrClassName()}"
                      }
                    }
                  )

                  KurobaComposeIcon(
                    modifier = Modifier
                      .size(80.dp, 80.dp)
                      .align(Alignment.Center),
                    drawableId = R.drawable.ic_baseline_warning_24
                  )

                  return@SubcomposeAsyncImage
                }

                SubcomposeAsyncImageContent()
              }
            )
          }
        }
      }
    }
  }

  companion object {
    const val ON_CAPTCHA_SOLVED = "on_captcha_solved"
    const val ON_SCREEN_DISMISSED = "on_screen_dismissed"

    val SCREEN_KEY = ScreenKey("DvachCaptchaScreen")
  }
}