package com.github.k1rakishou.kurobaexlite.features.captcha

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.managers.Captcha
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeError
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeSnappingSlider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import java.util.Locale
import org.koin.androidx.viewmodel.ext.android.viewModel

class Chan4CaptchaScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val chanDescriptor: ChanDescriptor,
  private val onCaptchaSolved: (Captcha) -> Unit,
  private val onScreenDismissed: () -> Unit
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val chan4CaptchaViewModel: Chan4CaptchaViewModel by componentActivity.viewModel()

  override val screenKey: ScreenKey = SCREEN_KEY

  override suspend fun onDispose() {
    super.onDispose()

    chan4CaptchaViewModel.resetCaptchaIfCaptchaIsAlmostDead(chanDescriptor)
    chan4CaptchaViewModel.cleanup()

    onScreenDismissed()
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
      key1 = chanDescriptor,
      block = { chan4CaptchaViewModel.requestCaptcha(chanDescriptor, forced = false) }
    )

    BuildCaptchaWindowImageOrText()

    Spacer(modifier = Modifier.height(8.dp))

    BuildCaptchaWindowSliderOrInput()

    BuildCaptchaWindowFooter()

    Spacer(modifier = Modifier.height(8.dp))
  }

  @Composable
  private fun BuildCaptchaWindowSliderOrInput() {
    val chanTheme = LocalChanTheme.current
    val captchaInfoAsync by chan4CaptchaViewModel.captchaInfoToShow
    val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

    if (captchaInfo != null && !captchaInfo.isNoopChallenge()) {
      var currentInputValue by captchaInfo.currentInputValue
      val scrollValueState = captchaInfo.sliderValue

      KurobaComposeTextField(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 16.dp),
        value = currentInputValue,
        onValueChange = { newValue -> currentInputValue = newValue.uppercase(Locale.ENGLISH) },
        keyboardActions = KeyboardActions(
          onDone = { verifyCaptcha(captchaInfo, currentInputValue) }
        ),
        keyboardOptions = KeyboardOptions(
          autoCorrect = false,
          keyboardType = KeyboardType.Password
        ),
        maxLines = 1,
        singleLine = true
      )

      Spacer(modifier = Modifier.height(8.dp))

      if (captchaInfo.needSlider()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val widthDiff = captchaInfo.widthDiff()

          val slideSteps = widthDiff
            ?: (constraints.maxWidth / PIXELS_PER_STEP)

          KurobaComposeSnappingSlider(
            slideOffsetState = scrollValueState,
            slideSteps = slideSteps.coerceAtLeast(MIN_SLIDE_STEPS),
            backgroundColor = chanTheme.backColorCompose,
            modifier = Modifier
              .wrapContentHeight()
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            onValueChange = { newValue -> scrollValueState.value = newValue }
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }

  @Composable
  private fun BuildCaptchaWindowFooter() {
    val captchaInfoAsync by chan4CaptchaViewModel.captchaInfoToShow
    val captchaInfo = (captchaInfoAsync as? AsyncData.Data)?.data

    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      val captchaTtlMillis by chan4CaptchaViewModel.captchaTtlMillisFlow.collectAsState()
      if (captchaTtlMillis >= 0L) {
        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeText(
          text = "${captchaTtlMillis / 1000L} sec",
          fontSize = 13.sp,
          modifier = Modifier.align(Alignment.CenterVertically)
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        modifier = Modifier.wrapContentSize(),
        onClick = {
          chan4CaptchaViewModel.requestCaptcha(chanDescriptor, forced = true)
        },
        text = stringResource(id = R.string.chan4_captcha_layout_reload)
      )

      Spacer(modifier = Modifier.width(8.dp))

      val buttonEnabled = captchaInfo != null
        && (captchaInfo.isNoopChallenge() || captchaInfo.currentInputValue.value.isNotEmpty())

      KurobaComposeTextBarButton(
        modifier = Modifier.wrapContentSize(),
        onClick = {
          val currentInputValue = captchaInfo?.currentInputValue
            ?: return@KurobaComposeTextBarButton

          verifyCaptcha(captchaInfo, currentInputValue.value)
        },
        enabled = buttonEnabled,
        text = stringResource(id = R.string.chan4_captcha_layout_verify)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun BuildCaptchaWindowImageOrText() {
    val captchaInfoAsync by chan4CaptchaViewModel.captchaInfoToShow
    var height by remember { mutableStateOf(160.dp) }

    BoxWithConstraints(
      modifier = Modifier
        .wrapContentHeight()
        .height(height)
    ) {
      val size = with(LocalDensity.current) {
        remember(key1 = maxWidth, key2 = maxHeight) {
          IntSize(maxWidth.toPx().toInt(), maxHeight.toPx().toInt())
        }
      }

      if (size != IntSize.Zero) {
        val captchaInfo = when (val cia = captchaInfoAsync) {
          AsyncData.Uninitialized,
          AsyncData.Loading -> {
            KurobaComposeLoadingIndicator()
            null
          }
          is AsyncData.Error -> {
            KurobaComposeError(
              modifier = Modifier.fillMaxSize(),
              errorMessage = cia.error.errorMessageOrClassName()
            )

            null
          }
          is AsyncData.Data -> cia.data
        }

        if (captchaInfo != null) {
          if (captchaInfo.isNoopChallenge()) {
            Box(modifier = Modifier
              .fillMaxWidth()
              .height(128.dp)
              .align(Alignment.Center)
            ) {
              KurobaComposeText(
                text = stringResource(id = R.string.chan4_captcha_layout_verification_not_required),
                textAlign = TextAlign.Center,
                modifier = Modifier
                  .fillMaxWidth()
              )
            }
          } else {
            height = 160.dp
            BuildCaptchaImageNormal(captchaInfo, size)
          }
        }
      }
    }
  }

  @Composable
  private fun BuildCaptchaImageNormal(
    captchaInfo: Chan4CaptchaViewModel.CaptchaInfo,
    size: IntSize
  ) {
    val imgBitmapPainter = captchaInfo.imgBitmapPainter!!

    val scale = Math.min(
      size.width.toFloat() / imgBitmapPainter.intrinsicSize.width,
      size.height.toFloat() / imgBitmapPainter.intrinsicSize.height
    )

    val contentScale = Scale(scale)
    var scrollValue by captchaInfo.sliderValue

    if (captchaInfo.bgBitmapPainter != null) {
      val bgBitmapPainter = captchaInfo.bgBitmapPainter
      val offset = remember(key1 = scrollValue) {
        val xOffset = (captchaInfo.bgInitialOffset + MIN_OFFSET + (scrollValue * MAX_OFFSET * -1f)).toInt()
        IntOffset(x = xOffset, y = 0)
      }

      Image(
        modifier = Modifier
          .fillMaxSize()
          .offset { offset },
        painter = bgBitmapPainter,
        contentScale = contentScale,
        contentDescription = null,
      )
    }

    val scrollState = rememberScrollableState { delta ->
      var newScrollValue = scrollValue + ((delta * 2f) / size.width.toFloat())

      if (newScrollValue < 0f) {
        newScrollValue = 0f
      } else if (newScrollValue > 1f) {
        newScrollValue = 1f
      }

      scrollValue = newScrollValue

      return@rememberScrollableState delta
    }

    Image(
      modifier = Modifier
        .fillMaxSize()
        .scrollable(state = scrollState, orientation = Orientation.Horizontal),
      painter = captchaInfo.imgBitmapPainter,
      contentScale = contentScale,
      contentDescription = null
    )
  }

  private fun verifyCaptcha(
    captchaInfo: Chan4CaptchaViewModel.CaptchaInfo?,
    currentInputValue: String
  ) {
    if (captchaInfo == null) {
      return
    }

    val challenge = captchaInfo.challenge
    val solution = CaptchaSolution.ChallengeWithSolution(
      challenge = challenge,
      solution = currentInputValue
    )

    val ttlMillis = captchaInfo.ttlMillis()
    if (ttlMillis <= 0L) {
      snackbarManager.toast(
        screenKey = snackbarManager.screenKeyFromDescriptor(chanDescriptor),
        messageId = R.string.chan4_captcha_layout_captcha_already_expired
      )
      return
    }

    val captcha = Captcha.newSolvedCaptcha(captchaSolution = solution, ttlMs = ttlMillis)
    chan4CaptchaViewModel.resetCaptchaForced(chanDescriptor)
    onCaptchaSolved(captcha)
    stopPresenting()
  }

  class Scale(
    private val scale: Float
  ) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
      return ScaleFactor(scale, scale)
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("Chan4CaptchaScreen")

    private const val MIN_OFFSET = 100f
    private const val MAX_OFFSET = 400f

    private const val MIN_SLIDE_STEPS = 25
    private const val PIXELS_PER_STEP = 50
  }

}