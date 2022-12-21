package com.github.k1rakishou.kurobaexlite.ui.helpers.dialog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeSnappingSlider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen

class SliderDialogScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : FloatingComposeScreen(
  screenArgs = screenArgs,
  componentActivity = componentActivity,
  navigationRouter = navigationRouter,
  canDismissByClickingOutside = true
) {
  override val screenKey: ScreenKey = SCREEN_KEY

  private val dialogTitle by argumentOrNullLazy<String>(DIALOG_TITLE)
  private val minValue by argumentOrDefaultLazy(MIN_VALUE, 0)
  private val currentValue by argumentOrDefaultLazy(CURRENT_VALUE, 0)
  private val maxValue by argumentOrDefaultLazy(MAX_VALUE, 100)
  private val sliderSteps by argumentOrNullLazy<Int>(SLIDER_STEPS)

  private var finishedWithValue = false

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    super.onDisposed(screenDisposeEvent)

    if (!finishedWithValue && screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      ScreenCallbackStorage.invokeCallback(screenKey, ON_RESULT, null)
    }
  }

  @Composable
  override fun FloatingContent() {
    super.FloatingContent()

    val slideOffsetState = remember {
      val sliderOffset = (currentValue - minValue).toFloat() / (maxValue - minValue).toFloat()
      return@remember mutableStateOf(sliderOffset)
    }
    var slideOffset by slideOffsetState

    val dialogTitleLocal = dialogTitle

    Column(
      modifier = Modifier.padding(
        horizontal = 16.dp,
        vertical = 8.dp
      )
    ) {
      if (dialogTitleLocal != null) {
        KurobaComposeText(
          text = dialogTitleLocal,
          fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))
      }

      Column {
        val currentValueFromOffset = (minValue + (slideOffset * (maxValue - minValue))).toInt()

        val currentValueFormatted = ScreenCallbackStorage.invokeCallbackWithResult<Int, String>(
          screenKey,
          CURRENT_VALUE_FORMATTER,
          currentValueFromOffset
        )

        if (currentValueFormatted != null) {
          KurobaComposeText(text = currentValueFormatted)
          Spacer(modifier = Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          KurobaComposeText(text = minValue.toString())
          Spacer(modifier = Modifier.width(20.dp))

          KurobaComposeSnappingSlider(
            modifier = Modifier.weight(1f),
            slideOffsetState = slideOffsetState,
            slideSteps = sliderSteps,
            onValueChange = { newValue -> slideOffset = newValue.coerceIn(0f, 1f) }
          )

          Spacer(modifier = Modifier.width(20.dp))
          KurobaComposeText(text = maxValue.toString())
        }

        Spacer(modifier = Modifier.height(16.dp))

      }

      Spacer(modifier = Modifier.height(32.dp))

      Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          text = stringResource(id = R.string.close),
          onClick = { stopPresenting() }
        )

        Spacer(modifier = Modifier.width(16.dp))

        KurobaComposeTextBarButton(
          text = stringResource(id = R.string.ok),
          onClick = {
            val resultValue = (minValue + (slideOffset * (maxValue - minValue))).toInt()

            finishedWithValue = true
            ScreenCallbackStorage.invokeCallback(screenKey, ON_RESULT, resultValue)
            stopPresenting()
          }
        )
      }
    }

  }

  companion object {
    val SCREEN_KEY = ScreenKey("SliderDialogScreen")

    const val DIALOG_TITLE = "dialog_title"
    const val MIN_VALUE = "min_value"
    const val CURRENT_VALUE = "current_value"
    const val MAX_VALUE = "max_value"
    const val SLIDER_STEPS = "slider_step"

    const val ON_RESULT = "on_result"
    const val CURRENT_VALUE_FORMATTER = "current_value_formatter"

    fun show(
      componentActivity: ComponentActivity,
      navigationRouter: NavigationRouter,
      title: String,
      minValue: Int,
      currentValue: Int,
      maxValue: Int,
      sliderSteps: Int? = null,
      currentValueFormatter: (Int) -> String,
      onResult: (Int?) -> Unit
    ) {
      val sliderDialogScreen = ComposeScreen.createScreen<SliderDialogScreen>(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        args = {
          putString(DIALOG_TITLE, title)
          putInt(MIN_VALUE, minValue)
          putInt(CURRENT_VALUE, currentValue)
          putInt(MAX_VALUE, maxValue)

          if (sliderSteps != null) {
            putInt(SLIDER_STEPS, sliderSteps)
          }
        },
        callbacks = {
          callback(ON_RESULT, onResult)
          callbackWithResult(CURRENT_VALUE_FORMATTER, currentValueFormatter)
        }
      )

      navigationRouter.presentScreen(sliderDialogScreen)
    }
  }
}