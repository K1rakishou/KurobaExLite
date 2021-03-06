package com.github.k1rakishou.kurobaexlite.ui.helpers.dialog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import java.util.concurrent.atomic.AtomicLong


// TODO(KurobaEx): screen parameters are not persisted across process death yet!
class DialogScreen(
  screenArgs: Bundle? = null,
  dialogKey: String = autoGeneratedDialogScreenKey(),
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  canDismissByClickingOutside: Boolean = true,
  private val params: Params,
  private val onDismissed: (() -> Unit)? = null
) : FloatingComposeScreen(
  screenArgs = screenArgs,
  componentActivity = componentActivity,
  navigationRouter = navigationRouter,
  canDismissByClickingOutside = canDismissByClickingOutside
) {
  private val dialogScreeKey = ScreenKey("DialogScreen_${dialogKey}")

  override val screenKey: ScreenKey = dialogScreeKey

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    super.onDisposed(screenDisposeEvent)

    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      onDismissed?.invoke()
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun FloatingContent() {
    val insets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(
      key1 = Unit,
      effect = { onDispose { keyboardController?.hide() } }
    )

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

    val dialogWidth = if (globalUiInfoManager.isTablet) {
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
        text = params.title.actualText(),
        fontSize = 18.sp
      )

      if (params.description != null) {
        Spacer(modifier = Modifier.height(4.dp))
        val maxHeight = (maxAvailableHeight() - insets.bottom - insets.top) / 2

        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(state = rememberScrollState()),
          text = params.description.actualText(),
          fontSize = 14.sp
        )
      }

      if (params.input != null && inputValueState != null) {
        val input = params.input
        var value by inputValueState

        val keyboardOptions = KeyboardOptions(
          keyboardType = when (input) {
            is Input.Number -> KeyboardType.Number
            is Input.String -> KeyboardType.Text
          }
        )

        Spacer(modifier = Modifier.height(4.dp))

        KurobaComposeTextField(
          modifier = Modifier.fillMaxWidth(),
          value = value,
          keyboardOptions = keyboardOptions,
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
            modifier = Modifier.wrapContentSize(),
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
            modifier = Modifier.wrapContentSize(),
            onClick = {
              params.neutralButton.onClick?.invoke()
              stopPresenting()
            },
            text = stringResource(id = params.neutralButton.buttonText)
          )

          Spacer(modifier = Modifier.height(8.dp))
        }

        val buttonTextColor = if (params.positiveButton.isActionDangerous) {
          chanTheme.accentColor
        } else {
          null
        }

        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          onClick = {
            params.positiveButton.onClick?.invoke(inputValueState?.value)
            stopPresenting()
          },
          customTextColor = buttonTextColor,
          text = stringResource(id = params.positiveButton.buttonText)
        )
      }
    }
  }

  class Params(
    val title: Text,
    val description: Text? = null,
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

  sealed class Text {

    @Composable
    fun actualText(): kotlin.String {
      return when (this) {
        is Id -> stringResource(id = textId)
        is String -> value
      }
    }

    data class Id(@StringRes val textId: Int) : Text()
    data class String(val value: kotlin.String) : Text()
  }

  class PositiveDialogButton(
    @StringRes val buttonText: Int,
    val isActionDangerous: Boolean = false,
    val onClick: ((result: String?) -> Unit)? = null
  )

  companion object {
    const val CATALOG_OVERFLOW_OPEN_THREAD_BY_IDENTIFIER = "catalog_overflow_open_thread_by_identifier_dialog"
    const val OPEN_EXTERNAL_THREAD = "open_external_thread"

    private val id = AtomicLong(0)

    fun autoGeneratedDialogScreenKey(): String {
      return "auto_generated_dialog_key_${id.getAndIncrement()}"
    }

    fun cancelButton(onClick: (() -> Unit)? = null): DialogButton {
      return DialogButton(buttonText = R.string.cancel, onClick = onClick)
    }

    fun okButton(
      isActionDangerous: Boolean = false,
      onClick: ((result: String?) -> Unit)? = null
    ): PositiveDialogButton {
      return PositiveDialogButton(
        buttonText = R.string.ok,
        isActionDangerous = isActionDangerous,
        onClick = onClick
      )
    }
  }

}