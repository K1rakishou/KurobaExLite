package com.github.k1rakishou.kurobaexlite.features.settings.report

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.managers.ReportManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.Collapsable
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCheckbox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.progress.ProgressScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class ReportIssueScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<ReportIssueScreen.ToolbarIcons>>(
  screenArgs,
  componentActivity,
  navigationRouter
) {
  private val reportManager: ReportManager by inject(ReportManager::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)

  private val defaultToolbarKey = "${screenKey.key}_toolbar"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val issueNumberState = mutableStateOf(TextFieldValue())
  private val reportTitleState = mutableStateOf(TextFieldValue())
  private val reportDescriptionState = mutableStateOf(TextFieldValue())
  private val reportLogsState = mutableStateOf(TextFieldValue())
  private val attachLogsState = mutableStateOf(true)

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .titleId(R.string.report_issue_screen_title)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.SendReport, drawableId = R.drawable.ic_baseline_send_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  override val defaultToolbar by lazy {
    SimpleToolbar(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<ToolbarIcons>>(screenKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { icon ->
          when (icon) {
            ToolbarIcons.Back -> { onBackPressed() }
            ToolbarIcons.SendReport -> { onSendReportClick() }
            ToolbarIcons.Overflow -> {
              // no-op
            }
          }
        }
      }
    )

    KurobaToolbarContainer(
      toolbarContainerKey = screenKey.key,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = { true }
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    GradientBackground(
      modifier = Modifier
        .fillMaxSize()
        .consumeClicks()
    ) {
      BuildContent()
    }
  }

  @Composable
  private fun BuildContent() {
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current
    val insets = LocalWindowInsets.current
    val paddings = remember { PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + insets.bottom) }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColor)
        .padding(paddings)
    ) {
      var issueNumber by issueNumberState
      var reportTitle by reportTitleState
      var reportDescription by reportDescriptionState
      var reportLogs by reportLogsState
      var attachLogs by attachLogsState

      var verboseLogs by remember { mutableStateOf(false) }
      var mpvLogs by remember { mutableStateOf(false) }
      var attachLogsWasReset by remember { mutableStateOf(false) }
      val issueNumberKbOptions = remember { KeyboardOptions(keyboardType = KeyboardType.Number) }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .background(chanTheme.accentColor.copy(alpha = 0.5f))
      ) {
        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(4.dp),
          text = stringResource(id = R.string.report_issue_screen_note),
          fontSize = 12.sp
        )
      }

      KurobaComposeCustomTextField(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        value = issueNumber,
        textColor = chanTheme.textColorPrimary,
        parentBackgroundColor = chanTheme.backColor,
        maxLines = 1,
        singleLine = true,
        maxTextLength = 10,
        labelText = stringResource(id = R.string.report_issue_screen_issue_number),
        keyboardOptions = issueNumberKbOptions,
        onValueChange = { number -> issueNumber = number }
      )

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeCustomTextField(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        value = reportTitle,
        enabled = issueNumber.text.isEmpty(),
        textColor = chanTheme.textColorPrimary,
        parentBackgroundColor = chanTheme.backColor,
        maxLines = 1,
        singleLine = true,
        maxTextLength = ReportManager.MAX_TITLE_LENGTH,
        labelText = stringResource(id = R.string.report_issue_screen_issue_title),
        onValueChange = { title -> reportTitle = title }
      )

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeCustomTextField(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        value = reportDescription,
        textColor = chanTheme.textColorPrimary,
        parentBackgroundColor = chanTheme.backColor,
        maxLines = 4,
        maxTextLength = ReportManager.MAX_DESCRIPTION_LENGTH,
        labelText = stringResource(id = R.string.report_issue_screen_issue_description),
        onValueChange = { description -> reportDescription = description }
      )

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = stringResource(id = R.string.report_issue_screen_attach_logs),
        currentlyChecked = attachLogs,
        onCheckChanged = { checked -> attachLogs = checked }
      )

      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = stringResource(id = R.string.report_issue_screen_verbose_logs),
        enabled = attachLogs,
        currentlyChecked = verboseLogs,
        onCheckChanged = { checked -> verboseLogs = checked }
      )

      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = stringResource(id = R.string.report_issue_screen_mpv_logs),
        enabled = attachLogs,
        currentlyChecked = mpvLogs,
        onCheckChanged = { checked -> mpvLogs = checked }
      )

      Spacer(modifier = Modifier.height(8.dp))

      LaunchedEffect(key1 = issueNumber, block = {
        if (issueNumber.text.isNotEmpty() && !attachLogsWasReset) {
          attachLogs = false
          attachLogsWasReset = true
        }
      })

      if (attachLogs) {
        LaunchedEffect(
          key1 = verboseLogs,
          key2 = mpvLogs,
          block = {
            reportLogs = withContext(Dispatchers.Default) {
              val logs = ReportManager.loadLogs(verboseLogs, mpvLogs)
              if (logs.isNullOrEmpty()) {
                return@withContext TextFieldValue()
              }

              val logsString = logs + reportManager.getReportFooter(context)
              return@withContext TextFieldValue(text = logsString)
            }
          })

        Collapsable(
          title = stringResource(id = R.string.report_issue_screen_last_logs),
          collapsedByDefault = true
        ) {
          if (reportLogs.text.isNotEmpty()) {
            KurobaComposeCustomTextField(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
              textColor = chanTheme.textColorPrimary,
              parentBackgroundColor = chanTheme.backColor,
              value = reportLogs,
              maxTextLength = ReportManager.MAX_LOGS_LENGTH,
              fontSize = 12.sp,
              onValueChange = { }
            )
          } else {
            KurobaComposeText(text = stringResource(id = R.string.report_issue_screen_loading_logs))
          }
        }
      }
    }
  }

  private fun onSendReportClick() {
    val issueNumberString = issueNumberState.value.text
    if (issueNumberString.isNotEmpty()) {
      sendReportComment(issueNumberString)
    } else {
      sendNewReport()
    }
  }

  private fun sendReportComment(issueNumberString: String) {
    val issueNumber = issueNumberString.toIntOrNull() ?: -1
    if (issueNumber < 0) {
      showToast("Bad issue number: \'$issueNumberString\'")
      return
    }

    val description = reportDescriptionState.value.text.take(ReportManager.MAX_DESCRIPTION_LENGTH)
    if (description.isEmpty()) {
      showToast(R.string.report_issue_screen_description_cannot_be_empty_error_comment_mode)
      return
    }

    val logs = if (attachLogsState.value) {
      reportLogsState.value.text.takeLast(ReportManager.MAX_LOGS_LENGTH)
    } else {
      null
    }

    val progressScreen = ComposeScreen.createScreen<ProgressScreen>(componentActivity, navigationRouter)
    navigationRouter.presentScreen(progressScreen)

    reportManager.sendComment(
      issueNumber = issueNumber,
      description = description,
      logs = logs,
      onReportSendResult = { result ->
        navigationRouter.stopPresentingScreen(progressScreen.screenKey)

        result
          .toastOnError { error ->
            val errorMessage = error.message ?: "No error message"

            appResources.string(
              R.string.report_issue_screen_error_while_trying_to_send_report,
              errorMessage
            )
          }
          .toastOnSuccess {
            appResources.string(R.string.report_issue_report_sent_message)
          }

        if (result.isSuccess) {
          navigationRouter.popScreen(this)
        }
      }
    )
  }

  private fun sendNewReport() {
    val title = reportTitleState.value.text.take(ReportManager.MAX_TITLE_LENGTH)
    if (title.isEmpty()) {
      showToast(R.string.report_issue_screen_title_cannot_be_empty_error)
      return
    }

    val logs = if (attachLogsState.value) {
      reportLogsState.value.text.takeLast(ReportManager.MAX_LOGS_LENGTH)
    } else {
      null
    }

    if (attachLogsState.value && logs.isNullOrEmpty()) {
      showToast(R.string.report_issue_screen_logs_are_empty_error)
      return
    }

    val description = reportDescriptionState.value.text.take(ReportManager.MAX_DESCRIPTION_LENGTH)
    if (description.isEmpty() && logs.isNullOrEmpty()) {
      showToast(R.string.report_issue_screen_description_cannot_be_empty_error)
      return
    }

    val progressScreen = ComposeScreen.createScreen<ProgressScreen>(componentActivity, navigationRouter)
    navigationRouter.presentScreen(progressScreen)

    reportManager.sendReport(
      title = title,
      description = description,
      logs = logs,
      onReportSendResult = { result ->
        navigationRouter.stopPresentingScreen(progressScreen.screenKey)

        result
          .toastOnError { error ->
            val errorMessage = error.message ?: "No error message"

            appResources.string(
              R.string.report_issue_screen_error_while_trying_to_send_report,
              errorMessage
            )
          }
          .toastOnSuccess {
            appResources.string(R.string.report_issue_report_sent_message)
          }

        if (result.isSuccess) {
          navigationRouter.popScreen(this)
        }
      }
    )
  }

  private fun showToast(messageId: Int) {
    showToast(appResources.string(messageId))
  }

  private fun showToast(message: String) {
    snackbarManager.toast(message)
  }

  enum class ToolbarIcons {
    Back,
    SendReport,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ReportIssueScreen")
  }

}