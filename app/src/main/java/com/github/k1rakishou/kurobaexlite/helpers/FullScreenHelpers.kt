package com.github.k1rakishou.kurobaexlite.helpers

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme

object FullScreenHelpers {

  fun Window.setupEdgeToEdge() {
    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
  }

  fun Window.setupStatusAndNavBarColors(theme: ChanTheme) {
    var newSystemUiVisibility = decorView.systemUiVisibility

    if (AndroidHelpers.isAndroidM()) {
      newSystemUiVisibility = if (theme.lightStatusBar) {
        newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
      } else {
        newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
      }
    }

    if (AndroidHelpers.isAndroidO()) {
      newSystemUiVisibility = if (theme.lightStatusBar) {
        newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
      } else {
        newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      }
    }

    decorView.systemUiVisibility = newSystemUiVisibility
  }

}