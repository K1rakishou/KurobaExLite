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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.features.settings.application.AppSettingsScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.RangeSetting
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

class SliderSettingItem(
  title: String,
  subtitle: AnnotatedString?,
  dependencies: List<BooleanSetting> = emptyList(),
  val delegate: RangeSetting,
  val enabled: Boolean = true,
  val showSliderDialog: suspend (AppSettingsScreenViewModel.SliderDialogParameters) -> Int?,
  val settingDisplayFormatter: (Int) -> String,
  val sliderCurrentValueFormatter: (Int) -> String,
  val onSettingUpdated: (suspend () -> Unit)?
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
              val sliderDialogParameters = AppSettingsScreenViewModel.SliderDialogParameters(
                title = title,
                delegate = delegate,
                currentValueFormatter = sliderCurrentValueFormatter
              )

              val result = showSliderDialog(sliderDialogParameters)
                ?: return@launch

              delegate.write(result)
              onSettingUpdated?.invoke()
            }
          }
        )
        .graphicsLayer { alpha = if (isSettingEnabled) 1f else disabledAlpha }
        .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fontSize = 16.sp,
        color = chanTheme.textColorPrimary,
        text = title
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

      if (value != null) {
        Spacer(modifier = Modifier.height(4.dp))

        val valueFormatted = remember(key1 = value) { settingDisplayFormatter(value) }

        KurobaComposeText(
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