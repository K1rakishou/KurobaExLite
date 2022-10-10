package com.github.k1rakishou.kurobaexlite.ui.activity

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.AppRestarter
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.ReportManager
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.helpers.Collapsable
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCheckbox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.ProvideAllTheStuff
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class CrashReportActivity : ComponentActivity() {
  private val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  private val fullScreenHelpers: FullScreenHelpers by inject(FullScreenHelpers::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val appRestarter: AppRestarter by inject(AppRestarter::class.java)
  private val reportManager: ReportManager by inject(ReportManager::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val bundle = intent.getBundleExtra(EXCEPTION_BUNDLE_KEY)
    if (bundle == null) {
      logcatError(TAG) { "Bundle is null" }

      finish()
      return
    }

    val className = bundle.getString(EXCEPTION_CLASS_NAME_KEY)
    val message = bundle.getString(EXCEPTION_MESSAGE_KEY)
    val stacktrace = bundle.getString(EXCEPTION_STACKTRACE_KEY)

    if (className == null || message == null || stacktrace == null) {
      logcatError(TAG) {
        "Bad bundle params. " +
          "className is null (${className == null}), " +
          "message is null (${message == null}), " +
          "stacktrace is null (${stacktrace == null})"
      }

      finish()
      return
    }

    logcat(TAG) { "Got new exception: ${className}" }

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
          KurobaComposeFadeIn {
            ContentInternal(
              className = className,
              message = message,
              stacktrace = stacktrace
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    appRestarter.detachActivity()
  }

  @Composable
  private fun ContentInternal(
    className: String,
    message: String,
    stacktrace: String
  ) {
    val insets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val contentPadding = remember(key1 = insets) {
      insets.asPaddingValues(consumeLeft = true, consumeRight = true)
    }

    var reportHandled by rememberSaveable { mutableStateOf(false) }
    var attachVerboseLogs by rememberSaveable { mutableStateOf(false) }
    var attachMpvLogs by rememberSaveable { mutableStateOf(false) }

    var blockSendReportButton by rememberSaveable { mutableStateOf(false) }
    var blockRestartAppButton by rememberSaveable { mutableStateOf(false) }

    var logsMut by rememberSaveable(attachVerboseLogs) { mutableStateOf<String?>(null) }
    val logs = logsMut

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
            .wrapContentSize(),
          contentAlignment = Alignment.Center
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
          val errorMessage = remember(key1 = className, key2 = message) {
            return@remember "Exception: ${className}\nMessage: ${message}"
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
          LaunchedEffect(
            key1 = Unit,
            block = {
              logsMut = withContext(Dispatchers.IO) {
                ReportManager.loadLogs(attachVerboseLogs = attachVerboseLogs, attachMpvLogs = attachMpvLogs)
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
          val footer = remember { reportManager.getReportFooter(context) }

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
            text = stringResource(id = R.string.crash_report_activity_send),
            enabled = !blockSendReportButton,
            onClick = {
              coroutineScope.launch {
                reportHandled = true

                blockSendReportButton = true
                blockRestartAppButton = true

                val logsForSending = if (logs.isNullOrEmpty()) {
                  withContext(Dispatchers.IO) { ReportManager.loadLogs(attachVerboseLogs, attachMpvLogs) }
                } else {
                  logs
                }

                val reportFooter = reportManager.getReportFooter(context)
                val title = "${className} ${message}"

                val body = buildString(4096) {
                  appendLine("Stacktrace")
                  appendLine("```")
                  appendLine(stacktrace)
                  appendLine("```")
                  appendLine()

                  if (logsForSending.isNotNullNorEmpty()) {
                    appendLine("Logs")
                    appendLine("```")
                    appendLine(logsForSending)
                    appendLine("```")
                  }

                  appendLine("Additional information")
                  appendLine("```")
                  appendLine(reportFooter)
                  appendLine("```")
                }

                reportManager.sendCrashlog(
                  title = title,
                  body = body,
                  onReportSendResult = { sendReportResult ->
                    if (sendReportResult.isFailure) {
                      blockSendReportButton = false
                      blockRestartAppButton = false

                      Toast.makeText(
                        this@CrashReportActivity,
                        "Failed to send report, error: " +
                          "${sendReportResult.exceptionOrThrow().errorMessageOrClassName()}",
                        Toast.LENGTH_LONG
                      ).show()
                    } else {
                      blockRestartAppButton = false

                      Toast.makeText(
                        this@CrashReportActivity,
                        "Report sent",
                        Toast.LENGTH_LONG
                      ).show()
                    }
                  }
                )
              }
            }
          )

          Spacer(modifier = Modifier.height(8.dp))

          KurobaComposeTextButton(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(id = R.string.crash_report_activity_copy_for_github),
            onClick = {
              reportHandled = true

              coroutineScope.launch {
                copyLogsFormattedToClipboard(
                  context = context,
                  className = className,
                  message = message,
                  stacktrace = stacktrace,
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
            enabled = !blockRestartAppButton,
            onClick = { appRestarter.restart() }
          )
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }

  private suspend fun copyLogsFormattedToClipboard(
    context: Context,
    className: String,
    message: String,
    stacktrace: String,
    attachVerboseLogs: Boolean,
    attachMpvLogs: Boolean
  ) {
    val logs = withContext(Dispatchers.IO) { ReportManager.loadLogs(attachVerboseLogs, attachMpvLogs) }
    val reportFooter = reportManager.getReportFooter(context)

    val resultString = buildString(16000) {
      appendLine("Title (put this into the Github issue title)")
      appendLine("Exception: ${className}")
      appendLine("Message: ${message}")
      appendLine()

      appendLine("Stacktrace")
      appendLine("```")
      appendLine(stacktrace)
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

  companion object {
    private const val TAG = "CrashReportActivity"

    const val EXCEPTION_BUNDLE_KEY = "exception_bundle"
    const val EXCEPTION_CLASS_NAME_KEY = "exception_class_name"
    const val EXCEPTION_MESSAGE_KEY = "exception_message"
    const val EXCEPTION_STACKTRACE_KEY = "exception_stacktrace"

  }

}