package com.github.k1rakishou.kurobaexlite.ui.screens

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.SplitScreenLayout

class HomeScreen(
  componentActivity: ComponentActivity
) : ComposeScreen(componentActivity) {
  private val portraitChildScreens = listOf<ComposeScreen>(
    DrawerScreen(componentActivity),
    CatalogScreen(componentActivity),
    ThreadScreen(componentActivity),
    AlbumScreen(componentActivity)
  )

  private val albumChildScreens = listOf<ComposeScreen>(
    DrawerScreen(componentActivity),
    SplitScreenLayout(
      componentActivity = componentActivity,
      orientation = SplitScreenLayout.Orientation.Horizontal,
      childScreens = listOf(
        SplitScreenLayout.ChildScreen(CatalogScreen(componentActivity), 0.4f),
        SplitScreenLayout.ChildScreen(ThreadScreen(componentActivity), 0.6f)
      )
    ),
    AlbumScreen(componentActivity)
  )

  override val screenKey: ScreenKey = SCREEN_KEY

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val childScreens = with(LocalConfiguration.current) {
      remember(key1 = orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          portraitChildScreens
        } else {
          albumChildScreens
        }
      }
    }

    HorizontalPager(
      modifier = Modifier.fillMaxSize(),
      count = childScreens.size
    ) { page ->
      childScreens[page].Content()
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("HomeScreen")
  }
}