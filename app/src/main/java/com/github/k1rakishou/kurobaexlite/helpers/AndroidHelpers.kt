package com.github.k1rakishou.kurobaexlite.helpers

import android.app.ActivityManager
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import logcat.logcat
import java.io.File

class AndroidHelpers(
  private val application: Application,
  private val snackbarManager: SnackbarManager
) {
  private var startTime = SystemClock.elapsedRealtime()

  private val applicationContext: Context
    get() = application.applicationContext

  private val clipboardManager by lazy { application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
  private val activityManager by lazy { application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }

  val isSlowDevice by lazy { activityManager.isLowRamDevice || !isAndroidM() }

  val appRunningTime: Long
    get() {
      return SystemClock.elapsedRealtime() - startTime
    }

  fun getApiLevel(): Int {
    return Build.VERSION.SDK_INT
  }

  // Api 22
  fun isAndroidL_MR1(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
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
  fun isAndroidQ(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  }

  // Api 30
  fun isAndroidR(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
  }

  // Api 31
  fun isAndroidS(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  }

  // Api 33
  fun isAndroidTiramisu(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  }

  fun getApplicationLabel(): String {
    return application.packageManager.getApplicationLabel(application.applicationInfo).toString()
  }

  fun getFlavorType(): FlavorType {
    return when (BuildConfig.FLAVOR_TYPE) {
      0 -> FlavorType.Development
      1 -> FlavorType.Production
      else -> throw RuntimeException("Unknown flavor type " + BuildConfig.FLAVOR_TYPE)
    }
  }

  fun isDevFlavor(): Boolean = getFlavorType() == FlavorType.Development

  fun File.availableSpaceInBytes(): Long {
    val stat = StatFs(this.path)
    return stat.availableBlocksLong * stat.blockSizeLong
  }

  fun openLink(context: Context, link: String): Boolean {
    if (TextUtils.isEmpty(link)) {
      logcat(TAG) { "openLink() link is empty" }

      snackbarManager.toast(
        screenKey = MainScreen.SCREEN_KEY,
        message = "Failed to open link because the link is empty"
      )
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

        snackbarManager.toast(
          screenKey = MainScreen.SCREEN_KEY,
          message = "Failed to open link because startActivity crashed with " +
            "'${e.errorMessageOrClassName(userReadable = true)}' error"
        )
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

      snackbarManager.toast(
        screenKey = MainScreen.SCREEN_KEY,
        message = "Failed to open link because no apps were found to open it with"
      )
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

      snackbarManager.toast(
        screenKey = MainScreen.SCREEN_KEY,
        message = "Failed to open intent because startActivity crashed with " +
          "'${e.errorMessageOrClassName(userReadable = true)}' error"
      )

      return false
    }
  }

  fun getDisplayFps(): Int {
    if (isSlowDevice || !isAndroidM()) {
      return 30
    }

    val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return wm.defaultDisplay.mode.refreshRate.toInt()
  }

  fun copyToClipboard(label: String, content: String) {
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, content))
  }

  fun clipboardContent(): String? {
    val primary = clipboardManager.primaryClip
    if (primary != null && primary.itemCount > 0) {
      val text = primary.getItemAt(0).text
      if (!TextUtils.isEmpty(text)) {
        return primary.getItemAt(0).text.toString()
      }
    }

    return null
  }

  fun navigationGesturesUsed(view: View): Boolean {
    val rootWindowInsets = ViewCompat.getRootWindowInsets(view)
      ?: return false

    val systemGestures = rootWindowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
    return systemGestures.left > 0 || systemGestures.right > 0
  }

  enum class FlavorType(val rawType: Int) {
    Production(0),
    Development(1)
  }

  companion object {
    private const val TAG = "AndroidHelpers"
  }

}