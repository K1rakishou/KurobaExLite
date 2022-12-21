package com.github.k1rakishou.kurobaexlite.features.settings.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.MapSetting
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MapSettingItem<K, V>(
  title: String,
  subtitle: AnnotatedString? = null,
  dependencies: List<BooleanSetting> = emptyList(),
  val delegate: MapSetting<K, V>,
  val enabled: Boolean = true,
  val showDialogScreen: suspend (DialogScreen.Params) -> Unit,
  val keyMapperFrom: (K) -> String,
  val keyMapperTo: (String) -> K,
  val valueMapperFrom: (V) -> String,
  val valueMapperTo: (String) -> V,
  val valueFormatter: (String) -> String = { it },
  val onSettingUpdated: (suspend () -> Unit)? = null
) : SettingItem(delegate.settingKey, title, subtitle, dependencies) {

  @Composable
  override fun Content() {
    super.Content()

    val chanTheme = LocalChanTheme.current
    val coroutineScope = rememberCoroutineScope()

    val valueMut by delegate.listen().collectAsState(initial = emptyMap())
    val settingValue = valueMut

    val isSettingEnabled by dependenciesEnabledState
    val disabledAlpha = ContentAlpha.disabled

    for ((index, entry) in settingValue.entries.withIndex()) {
      key(entry.key) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        ) {
          MapSettingEntry(
            isSettingEnabled = isSettingEnabled,
            coroutineScope = coroutineScope,
            entry = entry,
            disabledAlpha = disabledAlpha,
            chanTheme = chanTheme
          )

          if (index < settingValue.size - 1) {
            KurobaComposeDivider(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
            )
          }
        }
      }
    }
  }

  @Composable
  private fun MapSettingEntry(
    isSettingEnabled: Boolean,
    coroutineScope: CoroutineScope,
    entry: Map.Entry<K, V>,
    disabledAlpha: Float,
    chanTheme: ChanTheme
  ) {
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
                inputs = listOf(DialogScreen.Input.String(initialValue = valueMapperFrom(entry.value))),
                negativeButton = DialogScreen.DialogButton(
                  buttonText = R.string.cancel,
                  onClick = { /*No-op*/ }
                ),
                neutralButton = DialogScreen.DialogButton(
                  buttonText = R.string.remove,
                  onClick = { coroutineScope.launch { delegate.remove(entry.key) } }
                ),
                positiveButton = DialogScreen.PositiveDialogButton(
                  buttonText = R.string.ok,
                  onClick = { inputs ->
                    val input = inputs.firstOrNull()
                      ?: return@PositiveDialogButton

                    coroutineScope.launch {
                      delegate.put(entry.key, valueMapperTo(input))
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
      val formattedTitle = remember(key1 = entry.key) {
        buildString {
          append(title)
          append(" ")
          append("(")
          append(keyMapperFrom(entry.key))
          append(")")
        }
      }

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fontSize = 16.sp,
        color = chanTheme.textColorPrimary,
        text = formattedTitle
      )

      if (subtitle != null) {
        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          fontSize = 14.sp,
          color = chanTheme.textColorSecondary,
          text = subtitle
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      val formattedValue = remember(key1 = entry.value) {
        valueFormatter(valueMapperFrom(entry.value))
      }

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fontSize = 14.sp,
        color = chanTheme.textColorSecondary,
        text = formattedValue
      )
    }
  }
}