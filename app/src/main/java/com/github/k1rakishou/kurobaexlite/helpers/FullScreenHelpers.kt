package com.github.k1rakishou.kurobaexlite.helpers

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme

class FullScreenHelpers(
  private val androidHelpers: AndroidHelpers
) {

  fun setupEdgeToEdge(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
  }

  fun setupStatusAndNavBarColors(window: Window, theme: ChanTheme) {
    var newSystemUiVisibility = window.decorView.systemUiVisibility

    if (androidHelpers.isAndroidM()) {
      newSystemUiVisibility = if (theme.lightStatusBar) {
        newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
      } else {
        newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
      }
    }

    if (androidHelpers.isAndroidO()) {
      newSystemUiVisibility = if (theme.lightNavBar) {
        newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
      } else {
        newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      }
    }

    window.decorView.systemUiVisibility = newSystemUiVisibility
  }

}