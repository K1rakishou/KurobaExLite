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

    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
  }

  fun Window.setupStatusAndNavBarColors(theme: ChanTheme) {
    var newSystemUiVisibility = decorView.systemUiVisibility

    if (AndroidHelpers.isAndroidM()) {
      newSystemUiVisibility = when {
        theme.lightStatusBar -> {
          newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        else -> {
          newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
      }
    }

    if (AndroidHelpers.isAndroidO()) {
      newSystemUiVisibility = when {
        theme.lightNavBar -> {
          newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        else -> {
          newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
      }
    }

    decorView.systemUiVisibility = newSystemUiVisibility
  }

  fun Window.hideSystemUI(theme: ChanTheme) {
    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
      or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
      or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
      or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
      or View.SYSTEM_UI_FLAG_FULLSCREEN)

    setupStatusAndNavBarColors(theme)
  }

  fun Window.showSystemUI(theme: ChanTheme) {
    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

    setupStatusAndNavBarColors(theme)
  }

  fun Window.toggleSystemUI(theme: ChanTheme) {
    if (isSystemUIHidden()) {
      showSystemUI(theme)
    } else {
      hideSystemUI(theme)
    }
  }

  fun Window.isSystemUIHidden(): Boolean {
    return (decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_IMMERSIVE) != 0
  }

}