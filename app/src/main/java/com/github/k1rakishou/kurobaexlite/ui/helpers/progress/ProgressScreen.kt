package com.github.k1rakishou.kurobaexlite.ui.helpers.progress

import android.os.Bundle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen

class ProgressScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY

  private val title by argumentOrNullLazy<String>(TITLE)

  @Composable
  override fun FloatingContent() {
    val screenWidth = maxAvailableWidth()

    Column(
      modifier = Modifier
        .width(screenWidth)
        .wrapContentHeight()
        .padding(all = 8.dp)
    ) {
      val titleText = title ?: stringResource(id = R.string.loading_please_wait)

      KurobaComposeText(
        modifier = Modifier.fillMaxWidth(),
        text = titleText,
        fontSize = 16.sp
      )

      Spacer(modifier = Modifier.height(24.dp))

      KurobaComposeLoadingIndicator(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        fadeInTimeMs = 0
      )

      Spacer(modifier = Modifier.height(24.dp))
    }
  }

  companion object {
    const val TITLE = "title"

    val SCREEN_KEY = ScreenKey("ProgressScreen")
  }
}