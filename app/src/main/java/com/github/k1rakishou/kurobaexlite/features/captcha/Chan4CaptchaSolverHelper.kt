package com.github.k1rakishou.kurobaexlite.features.captcha

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.resumeValueSafe
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat


class Chan4CaptchaSolverHelper(
  private val moshi: Moshi,
  private val appContext: Context
) {
  @Volatile private var chan4CaptchaSolverInfo: CaptchaSolverInfo? = null

  suspend fun checkCaptchaSolverInstalled(): CaptchaSolverInfo {
    if (chan4CaptchaSolverInfo != null) {
      return chan4CaptchaSolverInfo!!
    }

    val intent = Intent(ACTION_GET_INFO)

    val resultBundle = sendBroadcastInternal(appContext, intent)
    if (resultBundle == null) {
      logcat(TAG) { "checkCaptchaSolverInstalled() resultIntent == null" }
      return CaptchaSolverInfo.NotInstalled
    }

    val appApiVersion = resultBundle.getInt(ACTION_GET_INFO_RESULT)
    if (appApiVersion != API_VERSION) {
      logcat(TAG) { "checkCaptchaSolverInstalled() appApiVersion (${appApiVersion}) != API_VERSION (${API_VERSION})" }
      chan4CaptchaSolverInfo = CaptchaSolverInfo.InstalledVersionMismatch(
        expected = API_VERSION,
        actual = appApiVersion
      )
      return chan4CaptchaSolverInfo!!
    }

    logcat(TAG) { "checkCaptchaSolverInstalled() true" }
    chan4CaptchaSolverInfo = CaptchaSolverInfo.Installed
    return chan4CaptchaSolverInfo!!
  }

  suspend fun autoSolveCaptcha(
    captchaInfoRawString: String,
    sliderOffset: Float?
  ): CaptchaSolution? {
    if (chan4CaptchaSolverInfo != CaptchaSolverInfo.Installed) {
      return null
    }

    logcat(TAG) { "autoSolveCaptcha() sliderOffset=${sliderOffset}" }

    val intent = Intent(ACTION_SOLVE_CAPTCHA).apply {
      putExtra(ACTION_SOLVE_CAPTCHA_JSON, captchaInfoRawString)

      if (sliderOffset != null) {
        putExtra(ACTION_SOLVE_CAPTCHA_SLIDER_OFFSET, sliderOffset)
      }
    }

    val resultBundle = sendBroadcastInternal(appContext, intent)
    if (resultBundle == null) {
      logcat(TAG) { "autoSolveCaptcha() resultIntent == null" }
      return null
    }

    val solvedCaptchaJson = resultBundle.getString(ACTION_SOLVE_CAPTCHA_RESULT)
    if (solvedCaptchaJson.isNullOrEmpty()) {
      logcat(TAG) { "autoSolveCaptcha() solvedCaptchaJson isNullOrEmpty" }
      return null
    }

    logcat(TAG) { "autoSolveCaptcha() got solvedCaptchaJson: ${solvedCaptchaJson}" }

    val captchaSolution = try {
      moshi.adapter(CaptchaSolution::class.java)
        .fromJson(solvedCaptchaJson)
    } catch (error: Throwable) {
      logcatError(TAG) {
        "autoSolveCaptcha() moshi.fromJson(CaptchaSolution) " +
          "error: ${error.asLogIfImportantOrErrorMessage()}"
      }

      null
    }

    return captchaSolution
  }

  private suspend fun sendBroadcastInternal(context: Context, intent: Intent): Bundle? {
    val broadcastReceiversInfo = context.packageManager.queryBroadcastReceivers(intent, 0)
    logcat(TAG) { "broadcastReceiversInfo=${broadcastReceiversInfo.size}" }

    val broadcastReceiver = broadcastReceiversInfo.firstOrNull()
      ?: return null

    logcat(TAG) { "Using packageName: ${broadcastReceiver.activityInfo.packageName}, "
      "name: ${broadcastReceiver.activityInfo.name}" }

    intent.component = ComponentName(
      broadcastReceiver.activityInfo.packageName,
      broadcastReceiver.activityInfo.name
    )

    return withTimeoutOrNull(10_000) {
      suspendCancellableCoroutine<Bundle?> { cancellableContinuation ->
        try {
          context.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
              override fun onReceive(context: Context?, resultIntent: Intent?) {
                cancellableContinuation.resumeValueSafe(getResultExtras(false))
              }
            },
            null,
            Activity.RESULT_OK,
            null,
            null
          )
        } catch (error: Throwable) {
          logcatError(TAG) {
            "sendBroadcastInternal() context.sendOrderedBroadcast() " +
              "action=${intent.action} error: ${error.asLogIfImportantOrErrorMessage()}"
          }

          cancellableContinuation.resumeValueSafe(null)
        }
      }
    }
  }

  @JsonClass(generateAdapter = true)
  class CaptchaSolution(
    @Json(name = "solutions")
    val solutions: List<String>,
    @Json(name = "slider_offset")
    val sliderOffset: Float?
  )

  companion object {
    private const val TAG = "Chan4CaptchaSolverHelper"

    // Change this when any public api of this app changes to avoid crashes
    private const val API_VERSION = 2
    private const val PACKAGE = "com.github.k1rakishou.chan4captchasolver"
    private const val ACTION_GET_INFO = "${PACKAGE}.get_info"
    private const val ACTION_GET_INFO_RESULT = "${PACKAGE}.get_info_result"

    private const val ACTION_SOLVE_CAPTCHA = "${PACKAGE}.solve_captcha"
    private const val ACTION_SOLVE_CAPTCHA_RESULT = "${PACKAGE}.solve_captcha_result"
    private const val ACTION_SOLVE_CAPTCHA_JSON = "${PACKAGE}.solve_captcha_json"
    private const val ACTION_SOLVE_CAPTCHA_SLIDER_OFFSET = "${PACKAGE}.solve_captcha_slider_offset"
  }

}

sealed class CaptchaSolverInfo {
  object NotInstalled : CaptchaSolverInfo()
  data class InstalledVersionMismatch(val expected: Int, val actual: Int) : CaptchaSolverInfo()
  object Installed : CaptchaSolverInfo()
}