package com.github.k1rakishou.kurobaexlite.helpers

import androidx.activity.ComponentActivity
import kotlin.system.exitProcess

class AppRestarter {
  private var activity: ComponentActivity? = null

  fun attachActivity(activity: ComponentActivity) {
    this.activity = activity
  }

  fun detachActivity() {
    this.activity = null
  }

  fun restart() {
    activity?.let { componentActivity ->
      val intent = componentActivity.packageManager.getLaunchIntentForPackage(componentActivity.packageName)
      componentActivity.finishAffinity()
      componentActivity.startActivity(intent)
      exitProcess(0)
    }
  }

}