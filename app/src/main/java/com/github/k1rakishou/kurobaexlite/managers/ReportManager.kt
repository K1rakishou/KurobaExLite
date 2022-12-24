package com.github.k1rakishou.kurobaexlite.managers

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.executors.SerializedCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.HashingUtil
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.source
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatterBuilder
import java.io.IOException
import java.nio.charset.StandardCharsets

class ReportManager(
  private val appScope: CoroutineScope,
  private val appContext: Context,
  private val androidHelpers: AndroidHelpers,
  private val proxiedOkHttpClient: IKurobaOkHttpClient,
  private val moshi: Moshi
) {
  private val activityManager: ActivityManager?
    get() = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(
    scope = appScope,
    dispatcher = Dispatchers.Default
  )

  fun sendComment(
    issueNumber: Int,
    description: String,
    logs: String?,
    onReportSendResult: (Result<Unit>) -> Unit
  ) {
    require(description.isNotEmpty() || logs != null) { "description is empty" }

    serializedCoroutineExecutor.post {
      val body = buildString(8192) {
        appendLine(description)

        if (logs.isNotNullNorEmpty()) {
          append("```")
          append(logs)
          append("```")
        }
      }

      val request = ReportRequest.comment(
        body = body
      )

      val result = sendInternal(
        reportRequest = request,
        issueNumber = issueNumber
      )

      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun sendCrashlog(
    title: String,
    body: String,
    onReportSendResult: (Result<Unit>) -> Unit
  ) {
    serializedCoroutineExecutor.post {
      val request = ReportRequest.crashLog(
        title = title,
        body = body
      )

      val result = sendInternal(request)
      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun sendReport(
    title: String,
    description: String,
    logs: String?,
    onReportSendResult: (Result<Unit>) -> Unit
  ) {
    require(title.isNotEmpty()) { "title is empty" }
    require(description.isNotEmpty() || logs != null) { "description is empty" }

    serializedCoroutineExecutor.post {
      val body = buildString(8192) {
        appendLine(description)

        if (logs.isNotNullNorEmpty()) {
          append("```")
          append(logs)
          append("```")
        }
      }

      val request = ReportRequest.report(
        title = title,
        body = body
      )

      val result = sendInternal(request)
      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun getReportFooter(context: Context): String {
    return buildString(capacity = 128) {
      appendLine("Android API Level: " + Build.VERSION.SDK_INT)
      appendLine("App Version: " + BuildConfig.VERSION_NAME)
      appendLine("Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL)
      appendLine("Flavor type: " + androidHelpers.getFlavorType().name)
      appendLine("isSlowDevice: ${androidHelpers.isSlowDevice}")
      appendLine("MemoryClass: ${activityManager?.memoryClass}")
      appendLine("App running time: ${formatAppRunningTime()}")
      appendLine("System animations info: ${systemAnimationsState(context)}")
    }
  }

  private fun formatAppRunningTime(): String {
    val time = androidHelpers.appRunningTime
    if (time <= 0) {
      return "${time} ms"
    }

    return appRunningTimeFormatter.print(Duration.millis(time).toPeriod())
  }

  private fun systemAnimationsState(context: Context): String {
    val duration = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE, 0f
    )

    val transition = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.TRANSITION_ANIMATION_SCALE, 0f
    )

    val window = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.WINDOW_ANIMATION_SCALE, 0f
    )

    return "duration: ${duration}, transition: ${transition}, window: ${window}"
  }

  private suspend fun sendInternal(reportRequest: ReportRequest, issueNumber: Int? = null): Result<Unit> {
    return Result.runCatching {
      val json = try {
        moshi.adapter(ReportRequest::class.java).toJson(reportRequest)
      } catch (error: Throwable) {
        logcatError(TAG) { "Couldn't convert $reportRequest to json, error: ${error.errorMessageOrClassName()}" }
        throw error
      }

      val reportUrl = if (issueNumber != null) {
        "https://api.github.com/repos/kurobaexreports/reports/issues/${issueNumber}/comments"
      } else {
        "https://api.github.com/repos/kurobaexreports/reports/issues"
      }
      val requestBody = json.toRequestBody("application/json".toMediaType())

      val request = Request.Builder()
        .url(reportUrl)
        .post(requestBody)
        .header("Accept", "application/vnd.github.v3+json")
        .header("Authorization", "token ${supersikritdonotlook()}")
        .build()

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request).unwrap()

      if (!response.isSuccessful) {
        val errorMessage = response.body
          ?.let { body -> moshi.adapter(ReportResponse::class.java).fromJson(body.string()) }
          ?.errorMessage

        val message = if (errorMessage.isNullOrEmpty()) {
          "Response is not successful. Status: ${response.code}"
        } else {
          "Response is not successful. Status: ${response.code}. ErrorMessage: '${errorMessage}'"
        }

        throw ReportError(message)
      }
    }
  }

  private fun supersikritdonotlook(): String {
    return HashingUtil.stringBase64Decode("Z2hwXzJMOUpaZ1ozM24xcDhBM3pwVnBYNmgwVkNPTzUzRzB1b2lCZA==")
  }

  private class ReportError(val errorMessage: String) : Exception(errorMessage)

  @JsonClass(generateAdapter = true)
  data class ReportRequest(
    @Json(name = "title")
    val title: String?,
    @Json(name = "body")
    val body: String,
    @Json(name = "labels")
    val labels: List<String>
  ) {

    companion object {

      fun crashLog(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("KurobaExLite", "New", "Crash")
        )
      }

      fun report(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("KurobaExLite", "New", "Report")
        )
      }

      fun comment(body: String): ReportRequest {
        return ReportRequest(
          title = null,
          body = body,
          labels = emptyList()
        )
      }

    }

  }

  @JsonClass(generateAdapter = true)
  data class ReportResponse(
    @Json(name = "message")
    val errorMessage: String?,
  )

  companion object {
    private const val TAG = "ReportManager"

    const val MAX_TITLE_LENGTH = 512
    const val MAX_DESCRIPTION_LENGTH = 8192
    const val MAX_LOGS_LENGTH = 50000

    private val appRunningTimeFormatter = PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendHours()
      .appendSuffix(":")
      .appendMinutes()
      .appendSuffix(":")
      .appendSeconds()
      .appendSuffix(".")
      .appendMillis3Digit()
      .toFormatter()

    fun loadLogs(attachVerboseLogs: Boolean, attachMpvLogs: Boolean): String? {
      val linesCount = if (attachVerboseLogs) {
        1000
      } else {
        500
      }

      val process = try {
        ProcessBuilder()
          .command("logcat", "-v", "tag", "-t", linesCount.toString(), "StrictMode:S")
          .start()
      } catch (e: IOException) {
        logcatError(TAG) { "Error starting logcat: ${e.asLogIfImportantOrErrorMessage()}" }
        return null
      }

      val outputStream = process.inputStream
      val fullLogsString = StringBuilder(16000)
      val logsAsString = outputStream.use { it.source().buffer().readString(StandardCharsets.UTF_8) }

      for (line in logsAsString.split("\n").toTypedArray()) {
        if (line.contains("${KurobaExLiteApplication.GLOBAL_TAG} |", ignoreCase = true)) {
          val logType = line.getOrNull(0)?.uppercaseChar() ?: continue

          when (logType) {
            'V' -> {
              if (!attachVerboseLogs) {
                continue
              }
            }
            else -> {
              // no-op
            }
          }

          fullLogsString.appendLine(line)
        } else if (attachMpvLogs && line.contains("mpv", ignoreCase = true)) {
          fullLogsString.appendLine(line)
        }
      }

      return fullLogsString.toString()
    }
  }

}