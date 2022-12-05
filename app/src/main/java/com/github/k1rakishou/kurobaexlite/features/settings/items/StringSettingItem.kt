package com.github.k1rakishou.kurobaexlite.features.settings.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

class StringSettingItem(
  title: String,
  subtitle: AnnotatedString? = null,
  dependencies: List<BooleanSetting> = emptyList(),
  val delegate: StringSetting,
  val enabled: Boolean = true,
  val showDialogScreen: suspend (DialogScreen.Params) -> Unit,
  val valueValidator: (String) -> Result<String> = { Result.success(it) },
  val settingDisplayFormatter: (String) -> String = { it },
  val onSettingUpdated: (suspend () -> Unit)? = null
) : SettingItem(delegate.settingKey, title, subtitle, dependencies) {

  @Composable
  override fun Content() {
    super.Content()

    val chanTheme = LocalChanTheme.current
    val coroutineScope = rememberCoroutineScope()

    val valueMut by delegate.listen().collectAsState(initial = null)
    val value = valueMut

    val isSettingEnabled by dependenciesEnabledState
    val disabledAlpha = ContentAlpha.disabled

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          enabled = isSettingEnabled,
          onClick = {
            coroutineScope.launch {
              val dialogParams = DialogScreen.Params(
                title = DialogScreen.Text.String("Enter value"),
                inputs = listOf(DialogScreen.Input.String(initialValue = value)),
                negativeButton = DialogScreen.DialogButton(
                  buttonText = R.string.cancel,
                  onClick = { /*No-op*/ }
                ),
                neutralButton = DialogScreen.DialogButton(
                  buttonText = R.string.reset,
                  onClick = { coroutineScope.launch { delegate.write(delegate.defaultValue) } }
                ),
                positiveButton = DialogScreen.PositiveDialogButton(
                  buttonText = R.string.ok,
                  onClick = { inputs ->
                    val input = inputs.firstOrNull() ?: return@PositiveDialogButton

                    coroutineScope.launch {
                      val formattedSetting = valueValidator(input)
                      if (formattedSetting.isFailure) {
                        snackbarManager.errorToast("Validation failed: ${formattedSetting.exceptionOrThrow().message ?: "No error message"}")
                        return@launch
                      }

                      delegate.write(formattedSetting.getOrThrow())
                    }
                  }
                )
              )

              showDialogScreen(dialogParams)
              onSettingUpdated?.invoke()
            }
          }
        )
        .graphicsLayer { alpha = if (isSettingEnabled) 1f else disabledAlpha }
        .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fontSize = 16.sp,
        color = chanTheme.textColorPrimary,
        text = title
      )

      if (subtitle != null) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          fontSize = 14.sp,
          color = chanTheme.textColorSecondary,
          text = subtitle
        )
      }

      if (value != null) {
        Spacer(modifier = Modifier.height(4.dp))

        val valueFormatted = remember(key1 = value) { "\"${settingDisplayFormatter(value)}\"" }

        Text(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          fontSize = 14.sp,
          color = chanTheme.textColorSecondary,
          text = valueFormatted
        )
      }
    }
  }

}