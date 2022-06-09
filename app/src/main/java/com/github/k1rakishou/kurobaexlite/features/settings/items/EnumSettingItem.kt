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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.helpers.settings.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.EnumSetting
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

class EnumSettingItem<T : Enum<T>>(
  title: String,
  subtitle: AnnotatedString?,
  dependencies: List<BooleanSetting>,
  val enabled: Boolean,
  val delegate: EnumSetting<T>,
  val settingNameMapper: (Enum<T>) -> String,
  val showOptionsScreen: suspend (List<FloatingMenuItem>) -> String?,
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
              val group = FloatingMenuItem.Group(
                checkedMenuItemKey = delegate.read().name,
                groupItems = delegate.enumValues.map { value ->
                  FloatingMenuItem.Text(
                    menuItemKey = value.name,
                    text = FloatingMenuItem.MenuItemText.String(settingNameMapper(value)),
                  )
                }
              )

              val selectedItem = showOptionsScreen(listOf(group))
                ?: return@launch

              val selectedEnumItem = delegate.enumValues.firstOrNull { it.name == selectedItem }
                ?: return@launch

              delegate.write(selectedEnumItem)
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
        Spacer(modifier = Modifier.height(8.dp))

        Text(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          fontSize = 14.sp,
          color = chanTheme.textColorSecondary,
          text = settingNameMapper(value)
        )
      }
    }
  }

}