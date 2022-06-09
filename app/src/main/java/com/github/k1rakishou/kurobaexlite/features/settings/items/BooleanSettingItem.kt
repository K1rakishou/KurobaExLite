package com.github.k1rakishou.kurobaexlite.features.settings.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.helpers.settings.BooleanSetting
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeSwitch
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

class BooleanSettingItem(
  title: String,
  subtitle: AnnotatedString? = null,
  enabled: Boolean = true,
  dependencies: List<BooleanSetting> = emptyList(),
  val delegate: BooleanSetting,
  val onSettingUpdated: (suspend () -> Unit)? = null
) : SettingItem(delegate.settingKey, title, subtitle, dependencies) {
  private val settingEnabledState = mutableStateOf(enabled)

  @Composable
  override fun Content() {
    super.Content()

    val chanTheme = LocalChanTheme.current
    val coroutineScope = rememberCoroutineScope()

    val valueMut by delegate.listen().collectAsState(initial = null)
    val value = valueMut

    val settingEnabled by settingEnabledState
    val dependenciesEnabled by dependenciesEnabledState

    val isSettingEnabled by remember { derivedStateOf { settingEnabled && dependenciesEnabled } }
    val disabledAlpha = ContentAlpha.disabled

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          enabled = isSettingEnabled,
          onClick = {
            coroutineScope.launch {
              delegate.toggle()
              onSettingUpdated?.invoke()
            }
          }
        )
        .graphicsLayer { alpha = if (isSettingEnabled) 1f else disabledAlpha }
        .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = 0.5f),
          fontSize = 16.sp,
          color = chanTheme.textColorPrimary,
          text = title
        )

        if (subtitle != null) {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .fillMaxHeight(fraction = 0.5f),
            fontSize = 14.sp,
            color = chanTheme.textColorSecondary,
            text = subtitle
          )
        }
      }

      if (value != null) {
        KurobaComposeSwitch(
          modifier = Modifier
            .wrapContentHeight()
            .width(64.dp),
          enabled = isSettingEnabled,
          checked = dependenciesEnabled && value,
          onCheckedChange = { newValue ->
            coroutineScope.launch {
              delegate.write(newValue)
              onSettingUpdated?.invoke()
            }
          }
        )
      } else {
        Spacer(modifier = Modifier.width(64.dp))
      }
    }
  }

}