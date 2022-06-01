package com.github.k1rakishou.kurobaexlite.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.groupOrNull
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.suspendConvertWithJsonAdapter
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import okhttp3.Request
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder

class UpdateManager(
  private val appContext: Context,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi
) {
  private val checking = AtomicBoolean(false)

  fun checkForUpdates(
    forced: Boolean = false,
    onFinished: ((UpdateCheckResult) -> Unit)? = null
  ) {
    appScope.launch {
      if (!checking.compareAndSet(false, true)) {
        return@launch
      }

      try {
        val result = checkForUpdatesInternal(forced)
        onFinished?.invoke(result)
      } finally {
        checking.set(false)
      }
    }
  }

  private suspend fun checkForUpdatesInternal(forced: Boolean): UpdateCheckResult {
    val currentTime = System.currentTimeMillis()
    val lastUpdateCheckTime = appSettings.lastUpdateCheckTime.read()

    if (!forced && (lastUpdateCheckTime + UPDATE_CHECK_INTERVAL_MS > currentTime)) {
      val deltaTime = currentTime - (lastUpdateCheckTime + UPDATE_CHECK_INTERVAL_MS)
      val timePeriod = Period(deltaTime.absoluteValue.coerceAtLeast(0))

      logcat(TAG) {
        "Can't check updates, last check was not too long ago, " +
          "time until next check: ${periodFormat.print(timePeriod)}"
      }

      return UpdateCheckResult.AlreadyCheckedRecently
    }

    appSettings.lastUpdateCheckTime.write(currentTime)

    val request = Request.Builder()
      .url(URL)
      .get()
      .build()

    val releasesAdapter = moshi.adapter<List<Release>>(
      Types.newParameterizedType(List::class.java, Release::class.java)
    )

    val latestRelease = proxiedOkHttpClient.okHttpClient()
      .suspendConvertWithJsonAdapter(request, releasesAdapter)
      .onFailure { error ->
        logcatError(TAG) {
          "Failed to request releases list from github, error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .getOrNull()
      ?.firstOrNull()

    if (latestRelease == null) {
      return UpdateCheckResult.Error("Failed to load latest release info from Github")
    }

    logcat(TAG) { "latestRelease: ${latestRelease}" }

    val tagName = latestRelease.tagName
      ?: return UpdateCheckResult.Error("Failed to find \'tagName\' in response from Github")
    val releaseUrl = latestRelease.releaseUrl
      ?: return UpdateCheckResult.Error("Failed to find \'releaseUrl\' in response from Github")
    val title = latestRelease.title
      ?: return UpdateCheckResult.Error("Failed to find \'title\' in response from Github")

    if (latestRelease.prerelease) {
      val notifyAboutBetaUpdates = appSettings.notifyAboutBetaUpdates.read()
      if (!notifyAboutBetaUpdates) {
        logcat(TAG) {
          "prerelease: ${latestRelease.prerelease}, " +
            "notifyAboutBetaUpdates: ${notifyAboutBetaUpdates}"
        }

        return UpdateCheckResult.Error("Latest release is a pre-release and updating to " +
          "pre-releases is disabled in the settings")
      }
    }


    val versionCodeFromServer = extractVersionCodeFromTag(latestRelease)
    if (versionCodeFromServer == null) {
      return UpdateCheckResult.Error("Failed to extract versionCode from latestRelease object")
    }

    val currentVersionCode = BuildConfig.VERSION_CODE

    val lastCheckedVersionCode = appSettings.lastCheckedVersionCode.read()
      .takeIf { lastVersionCode -> lastVersionCode > currentVersionCode }
      ?: currentVersionCode.toLong()

    if (versionCodeFromServer <= lastCheckedVersionCode) {
      logcat(TAG) {
        "versionCode ($versionCodeFromServer) <= lastCheckedVersionCode ($lastCheckedVersionCode), " +
          "currentVersionCode=${currentVersionCode}"
      }

      return UpdateCheckResult.AlreadyOnTheLatestVersion
    }

    logcat(TAG) {
      "lastCheckedVersionCode: ${lastCheckedVersionCode}, " +
        "newVersionCode: ${versionCodeFromServer}, " +
        "releaseUrl=${releaseUrl}"
    }

    showNotification(
      tagName = tagName,
      releaseUrl = releaseUrl,
      title = title
    )

    appSettings.lastCheckedVersionCode.write(versionCodeFromServer)
    return UpdateCheckResult.Success
  }

  private fun showNotification(
    tagName: String,
    releaseUrl: String,
    title: String
  ) {
    setupChannels()

    val contentText = "New version is available: ${tagName}"

    val notificationBuilder = NotificationCompat.Builder(
      appContext,
      AppConstants.Notifications.Update.UPDATE_NOTIFICATION_CHANNEL_ID
    )
      .setContentTitle(title)
      .setContentText(contentText)
      .setWhen(System.currentTimeMillis())
      .setShowWhen(true)
      .setSmallIcon(R.drawable.ic_stat_notify_alert)
      .setAutoCancel(true)
      .setAllowSystemGeneratedContextualActions(false)
      .setCategory(Notification.CATEGORY_EVENT)
      .addOpenInBrowserAction(releaseUrl)

    notificationManagerCompat.notify(
      AppConstants.Notifications.Update.UPDATE_NOTIFICATION_TAG,
      AppConstants.Notifications.Update.UPDATE_NOTIFICATION_ID,
      notificationBuilder.build()
    )
  }

  private fun NotificationCompat.Builder.addOpenInBrowserAction(releaseUrl: String): NotificationCompat.Builder {
    val notificationIntent = Intent(Intent.ACTION_VIEW)
    notificationIntent.data = Uri.parse(releaseUrl)

    val flags = if (androidHelpers.isAndroidM()) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }

    val pendingIntent = PendingIntent.getActivity(appContext, 0, notificationIntent, flags)
    return addAction(0, "Open in browser", pendingIntent)
  }

  private fun setupChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    logcat(TAG) { "setupChannels() called" }

    AppConstants.Notifications.Update.UPDATE_NOTIFICATION_CHANNEL_ID.let { channelId ->
      if (notificationManagerCompat.getNotificationChannel(channelId) == null) {
        logcat(TAG) { "setupChannels() creating ${channelId} channel" }

        // notification channel for apk updates
        val apkUpdateChannel = NotificationChannel(
          channelId,
          AppConstants.Notifications.Update.UPDATE_NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_HIGH
        )

        apkUpdateChannel.setSound(null, null)
        apkUpdateChannel.enableLights(true)
        apkUpdateChannel.lightColor = Color.BLUE
        apkUpdateChannel.enableVibration(true)

        notificationManagerCompat.createNotificationChannel(apkUpdateChannel)
      }
    }
  }

  private fun extractVersionCodeFromTag(release: Release): Long? {
    val tagName = release.tagName
    if (tagName.isNullOrEmpty()) {
      logcatError(TAG) { "Bad tagName: '${tagName}', release: ${release}" }
      return null
    }

    val matcher = if (release.prerelease) {
      BETA_VERSION_CODE_PATTERN.matcher(tagName)
    } else {
      RELEASE_VERSION_CODE_PATTERN.matcher(tagName)
    }

    if (!matcher.find()) {
      logcatError(TAG) { "Failed to find version code in tagName: '${tagName}', release: ${release}" }
      return null
    }

    val versionCode = calculateVersionCode(matcher)
    if (versionCode == null) {
      logcatError(TAG) { "Failed to extract versionCode from tagName: '${tagName}', release: ${release}" }
      return null
    }

    return versionCode.toLong()
  }

  private fun calculateVersionCode(versionMatcher: Matcher): Int? {
    val patch = versionMatcher.groupOrNull(3)?.toIntOrNull() ?: return null
    val minor = versionMatcher.groupOrNull(2)?.toIntOrNull()?.times(100) ?: return null
    val major = versionMatcher.groupOrNull(1)?.toIntOrNull()?.times(10000) ?: return null

    return major + minor + patch
  }

  sealed class UpdateCheckResult {
    object Success : UpdateCheckResult()
    object AlreadyCheckedRecently : UpdateCheckResult()
    object AlreadyOnTheLatestVersion : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
  }

  @JsonClass(generateAdapter = true)
  data class Release(
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "html_url") val releaseUrl: String?,
    @Json(name = "name") val title: String?,
    @Json(name = "prerelease") val prerelease: Boolean,
  )

  companion object {
    private const val TAG = "UpdateManager"

    private val periodFormat = PeriodFormatterBuilder()
      .appendHours()
      .appendSeparator(":")
      .appendMinutes()
      .appendSeparator(":")
      .appendSecondsWithOptionalMillis()
      .toFormatter()

    private val UPDATE_CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
    private val URL = "https://api.github.com/repos/K1rakishou/KurobaExLite/releases"
    private val RELEASE_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})-release$")
    private val BETA_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})-beta$")
  }

}