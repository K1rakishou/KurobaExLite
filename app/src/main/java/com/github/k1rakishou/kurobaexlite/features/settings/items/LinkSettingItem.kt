package com.github.k1rakishou.kurobaexlite.features.settings.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable

class LinkSettingItem(
  key: String,
  title: String,
  subtitle: String?,
  val enabled: Boolean,
  val onClicked: () -> Unit
) : SettingItem(key, title, subtitle, emptyList()) {

  @Composable
  override fun Content() {
    super.Content()

    val chanTheme = LocalChanTheme.current
    val isSettingEnabled by dependenciesEnabledState
    val disabledAlpha = ContentAlpha.disabled

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          enabled = isSettingEnabled,
          onClick = onClicked
        )
        .graphicsLayer { alpha = if (isSettingEnabled) 1f else disabledAlpha }
        .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fontSize = 16.sp,
        color = chanTheme.textColorPrimaryCompose,
        text = title
      )

      if (subtitle != null) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          fontSize = 14.sp,
          color = chanTheme.textColorSecondaryCompose,
          text = subtitle
        )
      }
    }
  }

}