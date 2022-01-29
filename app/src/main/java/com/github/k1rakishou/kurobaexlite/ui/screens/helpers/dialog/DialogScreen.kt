package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen


class DialogScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val params: Params
) : FloatingComposeScreen(componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    val insets = LocalWindowInsets.current

    val inputValueState: MutableState<String>? = remember {
      val input = params.input

      if (input != null) {
        val initialValueString = when (input) {
          is Input.Number -> input.initialValue?.toString()
          is Input.String -> input.initialValue
        }

        return@remember mutableStateOf(initialValueString ?: "")
      } else {
        return@remember null
      }
    }

    val dialogWidth = if (uiInfoManager.isTablet) {
      val availableWidth = maxAvailableWidth()
      availableWidth - (availableWidth / 2)
    } else {
      maxAvailableWidth()
    }

    Column(
      modifier = Modifier
        .width(dialogWidth)
        .padding(all = 8.dp)
    ) {
      KurobaComposeText(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(id = params.title),
        fontSize = 18.sp
      )

      if (params.description != null) {
        Spacer(modifier = Modifier.height(4.dp))
        val maxHeight = (maxAvailableHeight() - insets.bottomDp - insets.topDp) / 2

        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(state = rememberScrollState()),
          text = stringResource(id = params.description),
          fontSize = 14.sp
        )
      }

      if (params.input != null && inputValueState != null) {
        val input = params.input
        var value by inputValueState
        Spacer(modifier = Modifier.height(4.dp))

        KurobaComposeTextField(
          modifier = Modifier.fillMaxWidth(),
          value = value,
          onValueChange = { newValue ->
            if (input is Input.Number && newValue.toIntOrNull() == null) {
              return@KurobaComposeTextField
            }

            value = newValue
          }
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        if (params.negativeButton != null) {
          KurobaComposeTextBarButton(
            onClick = {
              params.negativeButton.onClick?.invoke()
              stopPresenting()
            },
            text = stringResource(id = params.negativeButton.buttonText)
          )

          Spacer(modifier = Modifier.height(8.dp))
        }

        if (params.neutralButton != null) {
          KurobaComposeTextBarButton(
            onClick = {
              params.neutralButton.onClick?.invoke()
              stopPresenting()
            },
            text = stringResource(id = params.neutralButton.buttonText)
          )

          Spacer(modifier = Modifier.height(8.dp))
        }

        KurobaComposeTextBarButton(
          onClick = {
            params.positiveButton.onClick?.invoke(inputValueState?.value)
            stopPresenting()
          },
          text = stringResource(id = params.positiveButton.buttonText)
        )
      }
    }
  }

  class Params(
    @StringRes val title: Int,
    @StringRes val description: Int? = null,
    val input: Input? = null,
    val negativeButton: DialogButton? = null,
    val neutralButton: DialogButton? = null,
    val positiveButton: PositiveDialogButton
  )

  sealed class Input {
    class String(val initialValue: kotlin.String? = null) : Input()
    class Number(val initialValue: Int? = null) : Input()
  }

  class DialogButton(
    @StringRes val buttonText: Int,
    val onClick: (() -> Unit)? = null
  )

  class PositiveDialogButton(
    @StringRes val buttonText: Int,
    val isActionDangerous: Boolean = false,
    val onClick: ((result: String?) -> Unit)? = null
  )

  companion object {
    private val SCREEN_KEY = ScreenKey("DialogScreen")
  }

}