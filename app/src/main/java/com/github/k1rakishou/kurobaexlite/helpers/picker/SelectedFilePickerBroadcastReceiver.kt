package com.github.k1rakishou.kurobaexlite.helpers.picker

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class SelectedFilePickerBroadcastReceiver : BroadcastReceiver() {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val appSettings: AppSettings by inject(AppSettings::class.java)

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) {
      return
    }

    if (!androidHelpers.isAndroidL_MR1()) {
      logcat(TAG) { "Not Api 22, current Api: ${androidHelpers.getApiLevel()}" }
      return
    }

    val component = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
    if (component == null) {
      logcat(TAG) { "component == null" }
      return
    }

    logcat(TAG) {
      "Setting lastRememberedFilePicker to " +
        "(packageName=${component.packageName}, className=${component.className})"
    }

    runBlocking { appSettings.lastRememberedFilePicker.write(component.packageName) }
  }

  companion object {
    private const val TAG = "SelectedFilePickerBroadcastReceiver"
  }
}