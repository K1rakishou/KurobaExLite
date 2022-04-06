package com.github.k1rakishou.kurobaexlite.helpers

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.text.TextUtils
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import java.io.File
import kotlin.system.exitProcess
import logcat.logcat

class AndroidHelpers(
  private val application: Application,
  private val snackbarManager: SnackbarManager
) {
  private val clipboardManager by lazy { application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

  fun getApiLevel(): Int {
    return Build.VERSION.SDK_INT
  }

  // Api 23
  fun isAndroidM(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
  }

  // Api 24
  fun isAndroidN(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
  }

  // Api 26
  fun isAndroidO(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
  }

  // Api 28
  fun isAndroidP(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
  }

  // Api 29
  fun isAndroid10(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  }

  // Api 30
  fun isAndroid11(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
  }

  fun setClipboardContent(label: String, content: String) {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, content))
  }

  fun getFlavorType(): FlavorType {
    return when (BuildConfig.FLAVOR_TYPE) {
      0 -> FlavorType.Stable
      1 -> FlavorType.Beta
      2 -> FlavorType.Dev
      else -> throw RuntimeException("Unknown flavor type " + BuildConfig.FLAVOR_TYPE)
    }
  }

  fun isDevFlavor(): Boolean = getFlavorType() == FlavorType.Dev

  fun getAvailableSpaceInBytes(file: File): Long {
    val stat = StatFs(file.path)
    return stat.availableBlocksLong * stat.blockSizeLong
  }

  fun restartApp(activity: Activity) {
    with(activity) {
      val intent = packageManager.getLaunchIntentForPackage(packageName)
      finishAffinity()
      startActivity(intent)
      exitProcess(0)
    }
  }

  fun openLink(context: Context, link: String): Boolean {
    if (TextUtils.isEmpty(link)) {
      logcat(TAG) { "openLink() link is empty" }
      snackbarManager.toast("Failed to open link because the link is empty")
      return false
    }

    val appContext = context.applicationContext
    val pm = appContext.packageManager
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
    val resolvedActivity = intent.resolveActivity(pm)

    if (resolvedActivity == null) {
      logcat(TAG) { "openLink() resolvedActivity == null" }

      try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return true
      } catch (e: Throwable) {
        logcatError(TAG) {
          "openLink() application.startActivity() " +
            "error: ${e.errorMessageOrClassName()}, intent: $intent"
        }

        snackbarManager.toast("Failed to open link because startActivity crashed with '${e.errorMessageOrClassName()}' error")
      }

      return false
    }

    val thisAppIsDefault = (resolvedActivity.packageName == appContext.packageName)
    if (!thisAppIsDefault) {
      logcat(TAG) { "openLink() thisAppIsDefault == false" }
      return openIntent(context, intent)
    }

    // Get all intents that match, and filter out this app
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val filteredIntents: MutableList<Intent> = ArrayList(resolveInfos.size)

    for (info in resolveInfos) {
      if (info.activityInfo.packageName != appContext.packageName) {
        val newIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        newIntent.setPackage(info.activityInfo.packageName)
        filteredIntents.add(newIntent)
      }
    }

    if (filteredIntents.size <= 0) {
      logcat(TAG) { "openLink() filteredIntents.size() <= 0" }
      snackbarManager.toast("Failed to open link because no apps were found to open it with")
      return false
    }

    // Create a chooser for the last app in the list, and add the rest with
    // EXTRA_INITIAL_INTENTS that get placed above
    val chooser = Intent.createChooser(
      filteredIntents.removeAt(filteredIntents.size - 1),
      null
    )

    chooser.putExtra(
      Intent.EXTRA_INITIAL_INTENTS,
      filteredIntents.toTypedArray()
    )

    val result = openIntent(context, chooser)
    logcat(TAG) { "openLink() success: ${result}" }

    return result
  }

  fun openIntent(context: Context, intent: Intent): Boolean {
    val appContext = context.applicationContext
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
      appContext.startActivity(intent)
      logcat(TAG) { "openIntent() success" }
      return true
    } catch (e: Throwable) {
      logcatError(TAG) {
        "openIntent() application.startActivity() " +
          "error:${e.errorMessageOrClassName()}, intent: $intent"
      }

      snackbarManager.toast("Failed to open intent because startActivity crashed with '${e.errorMessageOrClassName()}' error")
      return false
    }
  }

  enum class FlavorType {
    Stable,
    Beta,
    Dev
  }

  companion object {
    private const val TAG = "AndroidHelpers"
  }

}