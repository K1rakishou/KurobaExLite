package com.github.k1rakishou.kurobaexlite.features.captcha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.managers.Captcha
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowMainAxisAlignment
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowRow
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeError
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeSnappingSlider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class Chan4CaptchaScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val chan4CaptchaViewModel: Chan4CaptchaViewModel by componentActivity.viewModel()

  private val chanDescriptor: ChanDescriptor by requireArgumentLazy(CHAN_DESCRIPTOR_ARG)

  override val screenKey: ScreenKey = SCREEN_KEY

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      chan4CaptchaViewModel.resetCaptchaIfCaptchaIsAlmostDead(chanDescriptor)
      chan4CaptchaViewModel.cleanup()

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

    if (captchaInfo == null || captchaInfo.isNoopChallenge()) {
      return
    }

    var currentInputValue by captchaInfo.currentInputValue
    var prevSolution by remember { mutableStateOf<String?>(null) }
    val currentCaptchaSolution by captchaInfo.captchaSolution
    var captchaSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    val scrollValueState = captchaInfo.sliderValue

    LaunchedEffect(
      key1 = Unit,
      block = {
        snapshotFlow { currentCaptchaSolution }
          .collectLatest { captchaSolution ->
            captchaSuggestions = emptyList()

            if (captchaSolution == null) {
              return@collectLatest
            }

            val solution = captchaSolution.solutions.firstOrNull()

            if (solution == null || solution.isEmpty()) {
              snackbarManager.toast(R.string.chan4_captcha_layout_failed_to_find_solution)
              return@collectLatest
            }

            if (solution == prevSolution) {
              return@collectLatest
            }

            if (captchaSolution.solutions.size > 1) {
              val duplicates = mutableSetOf<String>()
              val actualSuggestions = mutableListOf<String>()

              for (suggestion in captchaSolution.solutions) {
                if (duplicates.add(suggestion)) {
                  actualSuggestions += suggestion
                }

                if (actualSuggestions.size >= 10) {
                  break
                }
              }

              if (actualSuggestions.isNotEmpty()) {
                captchaSuggestions = actualSuggestions
              }
            }

            prevSolution = solution
            currentInputValue = solution

            captchaSolution.sliderOffset?.let { sliderOffset ->
              scrollValueState.value = sliderOffset.coerceIn(0f, 1f)
            }
          }
      })

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

    if (captchaSuggestions.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))

      CaptchaSuggestions(
        currentInputValue = currentInputValue,
        captchaSuggestions = captchaSuggestions,
        onSuggestionClicked = { clickedSuggestion -> currentInputValue = clickedSuggestion }
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (captchaInfo.needSlider()) {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        KurobaComposeSnappingSlider(
          slideOffsetState = scrollValueState,
          slideSteps = SLIDE_STEPS,
          backgroundColor = chanTheme.backColor,
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

  @Composable
  private fun CaptchaSuggestions(
    currentInputValue: String,
    captchaSuggestions: List<String>,
    onSuggestionClicked: (String) -> Unit
  ) {
    FlowRow(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 16.dp),
      mainAxisAlignment = FlowMainAxisAlignment.Center,
      mainAxisSpacing = 4.dp,
      crossAxisSpacing = 4.dp
    ) {
      for (captchaSuggestion in captchaSuggestions) {
        key(captchaSuggestion) {
          CaptchaSuggestion(
            currentInputValue = currentInputValue,
            captchaSuggestion = captchaSuggestion,
            onSuggestionClicked = onSuggestionClicked
          )
        }
      }
    }
  }

  @Composable
  private fun CaptchaSuggestion(
    currentInputValue: String,
    captchaSuggestion: String,
    onSuggestionClicked: (String) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    Row(
      modifier = Modifier.wrapContentSize()
    ) {
      val bgColor = remember(key1 = currentInputValue, key2 = captchaSuggestion) {
        if (currentInputValue.equals(captchaSuggestion, ignoreCase = true)) {
          chanTheme.accentColor
        } else {
          chanTheme.backColorSecondary
        }
      }

      val textColor = remember(key1 = bgColor) {
        if (ThemeEngine.isDarkColor(bgColor)) {
          Color.White
        } else {
          Color.Black
        }
      }

      KurobaComposeCardView(
        modifier = Modifier
          .wrapContentSize()
          .kurobaClickable(bounded = true, onClick = { onSuggestionClicked(captchaSuggestion) }),
        backgroundColor = bgColor,
        shape = remember { RoundedCornerShape(4.dp) }
      ) {
        Text(
          modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
          text = captchaSuggestion,
          color = textColor,
          fontSize = 18.sp
        )
      }
    }
  }

  @Composable
  private fun BuildCaptchaWindowFooter() {
    val captchaInfoAsync by chan4CaptchaViewModel.captchaInfoToShow
    val solvingInProgress by chan4CaptchaViewModel.solvingInProgress
    val captchaSolverInstalled by chan4CaptchaViewModel.captchaSolverInstalled

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

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        text = stringResource(id = R.string.chan4_captcha_layout_reload),
        enabled = !solvingInProgress,
        onClick = {
          chan4CaptchaViewModel.requestCaptcha(chanDescriptor, forced = true)
        }
      )

      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        onClick = {
          if (captchaInfo?.captchaInfoRawString != null) {
            chan4CaptchaViewModel.solveCaptcha(
              captchaInfoRawString = captchaInfo.captchaInfoRawString,
              sliderOffset = captchaInfo.sliderValue.value
            )
          }
        },
        text = stringResource(id = R.string.chan4_captcha_layout_solve),
        enabled = !solvingInProgress &&
          captchaSolverInstalled &&
          captchaInfo != null &&
          captchaInfo.captchaInfoRawString != null
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        onClick = {
          val currentInputValue = captchaInfo?.currentInputValue
            ?: return@KurobaComposeTextBarButton

          verifyCaptcha(captchaInfo, currentInputValue.value)
        },
        enabled = captchaInfo != null
          && (captchaInfo.isNoopChallenge() || captchaInfo.currentInputValue.value.isNotEmpty())
          && !solvingInProgress,
        text = stringResource(id = R.string.chan4_captcha_layout_verify)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun BuildCaptchaWindowImageOrText() {
    val captchaInfoAsync by chan4CaptchaViewModel.captchaInfoToShow

    BoxWithConstraints(
      modifier = Modifier.wrapContentSize()
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
            KurobaComposeLoadingIndicator(
              modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            )

            null
          }
          is AsyncData.Error -> {
            KurobaComposeError(
              modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
              errorMessage = cia.error.errorMessageOrClassName(userReadable = true)
            )

            null
          }
          is AsyncData.Data -> cia.data
        }

        if (captchaInfo != null) {
          if (captchaInfo.isNoopChallenge()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .align(Alignment.Center)
                .padding(vertical = 16.dp)
            ) {
              KurobaComposeText(
                text = stringResource(id = R.string.chan4_captcha_layout_verification_not_required),
                textAlign = TextAlign.Center,
                modifier = Modifier
                  .fillMaxWidth()
              )
            }
          } else {
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
    val density = LocalDensity.current

    val width = captchaInfo.imgBitmap!!.width
    val height = captchaInfo.imgBitmap.height
    val th = 80
    val pw = 16
    val canvasScale = (th / height)
    val canvasHeight = th
    val canvasWidth = width * canvasScale + pw * 2

    val scale = Math.min(size.width.toFloat() / width, size.height.toFloat() / height)
    val canvasWidthDp = with(density) { (canvasWidth * scale).toDp() }
    val canvasHeightDp = with(density) { (canvasHeight * scale).toDp() }

    val scrollValue by captchaInfo.sliderValue

    Canvas(
      modifier = Modifier
        .size(canvasWidthDp, canvasHeightDp)
        .clipToBounds(),
      onDraw = {
        val canvas = drawContext.canvas.nativeCanvas

        canvas.withScale(x = scale, y = scale) {
          drawRect(Color(0xFFEEEEEE.toInt()))

          if (captchaInfo.bgBitmap != null) {
            canvas.withTranslation(x = (scrollValue * captchaInfo.widthDiff() * -1)) {
              canvas.drawBitmap(captchaInfo.bgBitmap, 0f, 0f, null)
            }
          }

          canvas.drawBitmap(captchaInfo.imgBitmap, 0f, 0f, null)
        }
      }
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
    ScreenCallbackStorage.invokeCallback(screenKey, ON_CAPTCHA_SOLVED, captcha)
    stopPresenting()
  }

  companion object {
    const val CHAN_DESCRIPTOR_ARG = "chan_descriptor"

    const val ON_CAPTCHA_SOLVED = "on_captcha_solved"
    const val ON_SCREEN_DISMISSED = "on_screen_dismissed"

    val SCREEN_KEY = ScreenKey("Chan4CaptchaScreen")

    private const val SLIDE_STEPS = 50
  }

}