package com.github.k1rakishou.kurobaexlite.ui.activity

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.AppRestarter
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCheckbox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.ProvideAllTheStuff
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.verticalScrollbar
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import okio.buffer
import okio.source
import org.joda.time.Duration
import org.joda.time.format.PeriodFormatterBuilder
import org.koin.java.KoinJavaComponent.inject

class CrashReportActivity : ComponentActivity() {
  private val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  private val fullScreenHelpers: FullScreenHelpers by inject(FullScreenHelpers::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val appRestarter: AppRestarter by inject(AppRestarter::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val throwable = intent.getBundleExtra(EXCEPTION_BUNDLE_KEY)
      ?.let { bundle -> bundle.getSerializable(EXCEPTION_KEY) as? Throwable }

    if (throwable == null) {
      finish()
      return
    }

    logcat(TAG) { "Got new throwable: ${throwable.errorMessageOrClassName()}" }

    WindowCompat.setDecorFitsSystemWindows(window, false)
    fullScreenHelpers.setupEdgeToEdge(window = window)
    fullScreenHelpers.setupStatusAndNavBarColors(theme = themeEngine.chanTheme, window = window)
    appRestarter.attachActivity(this)

    setContent {
      ProvideAllTheStuff(
        componentActivity = this,
        window = window,
        themeEngine = themeEngine,
        runtimePermissionsHelper = null
      ) {
        GradientBackground(
          modifier = Modifier.fillMaxSize()
        ) {
          Content(throwable)
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    appRestarter.detachActivity()
  }

  @Composable
  private fun Content(throwable: Throwable) {
    val insets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val contentPadding = remember(key1 = insets) {
      insets.asPaddingValues(consumeLeft = true, consumeRight = true)
    }

    var reportHandled by rememberSaveable { mutableStateOf(false) }
    var attachVerboseLogs by rememberSaveable { mutableStateOf(false) }
    var attachMpvLogs by rememberSaveable { mutableStateOf(false) }

    InsetsAwareBox(
      modifier = Modifier
        .fillMaxSize()
        .verticalScrollbar(
          contentPadding = contentPadding,
          scrollState = scrollState
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(), contentAlignment = Alignment.Center
        ) {
          KurobaComposeText(
            text = stringResource(id = R.string.crash_report_activity_title),
            color = chanTheme.accentColor,
            fontSize = 18.sp
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(
          title = stringResource(id = R.string.crash_report_activity_crash_message_section),
          collapsedByDefault = false
        ) {
          val errorMessage = remember(key1 = throwable) {
            val exceptionClassName = throwable.javaClass.name

            var message = if (throwable.cause?.message?.isNotNullNorBlank() == true) {
              throwable.cause!!.message
            } else {
              throwable.message
            }

            if (message.isNullOrEmpty()) {
              message = "<No error message>"
            }

            return@remember "Exception: ${exceptionClassName}\nMessage: ${message}"
          }

          SelectionContainer {
            KurobaComposeText(
              modifier = Modifier.fillMaxSize(),
              color = chanTheme.textColorSecondary,
              text = errorMessage
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(title = stringResource(id = R.string.crash_report_activity_crash_stacktrace_section)) {
          val stacktrace = remember(key1 = throwable) { throwable.stackTraceToString() }

          SelectionContainer {
            KurobaComposeText(
              modifier = Modifier.fillMaxSize(),
              color = chanTheme.textColorSecondary,
              text = stacktrace
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(title = stringResource(id = R.string.crash_report_activity_crash_logs_section)) {
          var logsMut by rememberSaveable(attachVerboseLogs) { mutableStateOf<String?>(null) }
          val logs = logsMut

          LaunchedEffect(
            key1 = Unit,
            block = {
              logsMut = withContext(Dispatchers.IO) {
                loadLogs(attachVerboseLogs = attachVerboseLogs, attachMpvLogs = attachMpvLogs)
              }
            }
          )

          if (logs == null) {
            KurobaComposeText(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              color = chanTheme.textColorSecondary,
              text = stringResource(id = R.string.crash_report_activity_loading_logs)
            )
          } else {
            SelectionContainer {
              KurobaComposeText(
                modifier = Modifier.fillMaxSize(),
                color = chanTheme.textColorSecondary,
                text = logs
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Collapsable(title = stringResource(id = R.string.crash_report_activity_additional_info_section)) {
          val footer = remember { getReportFooter() }

          SelectionContainer {
            KurobaComposeText(
              modifier = Modifier.fillMaxSize(),
              color = chanTheme.textColorSecondary,
              text = footer
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        KurobaComposeCheckbox(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
          text = stringResource(id = R.string.crash_report_activity_verbose_logs),
          currentlyChecked = attachVerboseLogs,
          onCheckChanged = { newValue -> attachVerboseLogs = newValue }
        )

        Spacer(modifier = Modifier.height(4.dp))

        KurobaComposeCheckbox(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
          text = stringResource(id = R.string.crash_report_activity_mpv_logs),
          currentlyChecked = attachMpvLogs,
          onCheckChanged = { newValue -> attachMpvLogs = newValue }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(id = R.string.crash_report_activity_copy_for_github),
            onClick = {
              reportHandled = true

              coroutineScope.launch {
                copyLogsFormattedToClipboard(
                  throwable = throwable,
                  attachVerboseLogs = attachVerboseLogs,
                  attachMpvLogs = attachMpvLogs
                )
              }
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(id = R.string.crash_report_activity_open_issue_tracker),
            onClick = {
              reportHandled = true

              androidHelpers.openLink(
                context = this@CrashReportActivity,
                link = AppConstants.CREATE_NEW_ISSUE_URL
              )
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          val buttonText = if (reportHandled) {
            stringResource(id = R.string.crash_report_activity_restart_the_app)
          } else {
            stringResource(id = R.string.crash_report_activity_restart_the_app_not_my_problem)
          }

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            text = buttonText,
            onClick = { appRestarter.restart() }
          )
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }

  @Composable
  private fun Collapsable(
    title: String,
    collapsedByDefault: Boolean = true,
    content: @Composable () -> Unit
  ) {
    var collapsed by rememberSaveable { mutableStateOf(collapsedByDefault) }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .animateContentSize()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .kurobaClickable(onClick = { collapsed = !collapsed }),
        verticalAlignment = Alignment.CenterVertically
      ) {
        KurobaComposeIcon(
          modifier = Modifier
            .graphicsLayer { rotationZ = if (collapsed) 0f else 90f },
          drawableId = R.drawable.ic_baseline_arrow_right_24
        )

        Spacer(modifier = Modifier.width(4.dp))

        KurobaComposeText(text = title)

        Spacer(modifier = Modifier.width(4.dp))

        KurobaComposeDivider(modifier = Modifier
          .weight(1f)
          .height(1.dp))
      }

      if (!collapsed) {
        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
          content()
        }
      }
    }
  }

  private suspend fun copyLogsFormattedToClipboard(
    throwable: Throwable,
    attachVerboseLogs: Boolean,
    attachMpvLogs: Boolean
  ) {
    val logs = withContext(Dispatchers.IO) { loadLogs(attachVerboseLogs, attachMpvLogs) }
    val reportFooter = getReportFooter()

    val resultString = buildString(16000) {
      appendLine("Title (put this into the Github issue title)")
      appendLine(throwable.message)
      appendLine()

      appendLine("Stacktrace")
      appendLine("```")
      appendLine(throwable.stackTraceToString())
      appendLine("```")
      appendLine()

      if (logs.isNotNullNorEmpty()) {
        appendLine("Logs")
        appendLine("```")
        appendLine(logs)
        appendLine("```")
      }

      appendLine("Additional information")
      appendLine("```")
      appendLine(reportFooter)
      appendLine("```")
    }

    androidHelpers.setClipboardContent("Crash report", resultString)

    Toast.makeText(
      this,
      resources.getString(R.string.crash_report_activity_copied_to_clipboard),
      Toast.LENGTH_SHORT
    ).show()
  }

  private fun getReportFooter(): String {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    return buildString(capacity = 128) {
      appendLine("Android API Level: " + Build.VERSION.SDK_INT)
      appendLine("App Version: " + BuildConfig.VERSION_NAME)
      appendLine("Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL)
      appendLine("Flavor type: " + androidHelpers.getFlavorType().name)
      appendLine("isSlowDevice: ${androidHelpers.isSlowDevice}")
      appendLine("MemoryClass: ${activityManager?.memoryClass}")
      appendLine("App running time: ${formatAppRunningTime()}")
    }
  }

  private fun formatAppRunningTime(): String {
    val time = androidHelpers.appRunningTime
    if (time <= 0) {
      return "Bad time: ${time}"
    }

    return appRunningTimeFormatter.print(Duration.millis(time).toPeriod())
  }

  companion object {
    private const val TAG = "CrashReportActivity"

    const val EXCEPTION_BUNDLE_KEY = "exception_bundle"
    const val EXCEPTION_KEY = "exception"

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