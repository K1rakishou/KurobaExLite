package com.github.k1rakishou.kurobaexlite.ui.helpers.progress

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen

class ProgressScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val title: String
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    val availableWidth = maxAvailableWidth()
    val screenWidth = availableWidth - (availableWidth / 2)

    Column(
      modifier = Modifier
        .width(screenWidth)
        .wrapContentHeight()
        .padding(all = 8.dp)
    ) {
      KurobaComposeText(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        fontSize = 16.sp
      )

      Spacer(modifier = Modifier.height(24.dp))

      KurobaComposeLoadingIndicator(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      )
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ProgressScreen")
  }
}