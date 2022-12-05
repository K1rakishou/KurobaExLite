package com.github.k1rakishou.kurobaexlite.features.login.dvach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaLabelText
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class DvachLoginScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val dvachLoginScreenViewModel: DvachLoginScreenViewModel by componentActivity.viewModel()

  private val passcodeCookieArg by requireArgumentLazy<String>(CURRENT_PASSCODE_COOKIE)

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    super.FloatingContent()

    val currentPasscodeCookieMut by dvachLoginScreenViewModel.passcodeCookieChangesFlow.collectAsState()
    val currentPasscodeCookie = currentPasscodeCookieMut

    val loggedIn by remember(key1 = currentPasscodeCookie) {
      val loggedIn = currentPasscodeCookie?.isNotEmpty()
        ?: passcodeCookieArg.isNotEmpty()

      return@remember mutableStateOf(loggedIn)
    }

    var blockInput by remember { mutableStateOf(false) }

    Column(
      modifier = Modifier
        .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
      if (loggedIn) {
        PasscodeCookieInput(
          blockInput = blockInput,
          currentPasscodeCookie = currentPasscodeCookie ?: "",
          onLogoutClicked = {
            handleLogout(
              onLogoutStart = { blockInput = true },
              onLogoutEnd = { blockInput = false },
            )
          }
        )
      } else {
        PasscodeInput(
          blockInput = blockInput,
          onLoginClicked = { passcode ->
            handleLogin(
              passcode = passcode,
              onLoginStart = { blockInput = true },
              onLoginEnd = { blockInput = false },
            )
          }
        )
      }
    }
  }

  @Composable
  private fun PasscodeInput(
    blockInput: Boolean,
    onLoginClicked: (String) -> Unit
  ) {
    var passcode by remember { mutableStateOf(TextFieldValue()) }

    KurobaComposeTextField(
      modifier = Modifier.fillMaxWidth(),
      value = passcode,
      enabled = !blockInput,
      onValueChange = { value -> passcode = value },
      label = { interactionSource ->
        KurobaLabelText(
          labelText = stringResource(id = R.string.dvach_setting_passcode_input_title),
          interactionSource = interactionSource
        )
      }
    )

    Spacer(modifier = Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      KurobaComposeTextBarButton(
        modifier = Modifier.wrapContentSize(),
        enabled = !blockInput,
        onClick = { stopPresenting() },
        text = stringResource(id = R.string.close)
      )

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeTextBarButton(
        modifier = Modifier.wrapContentSize(),
        enabled = !blockInput && passcode.text.isNotEmpty(),
        onClick = { onLoginClicked(passcode.text) },
        text = stringResource(id = R.string.login_screen_login)
      )
    }
  }

  @Composable
  private fun PasscodeCookieInput(
    blockInput: Boolean,
    currentPasscodeCookie: String,
    onLogoutClicked: () -> Unit
  ) {
    KurobaComposeTextField(
      modifier = Modifier.fillMaxWidth(),
      value = remember(key1 = currentPasscodeCookie) { TextFieldValue(currentPasscodeCookie) },
      enabled = !blockInput,
      onValueChange = { },
      label = { interactionSource ->
        KurobaLabelText(
          labelText = stringResource(id = R.string.chan4_setting_passcode_cookie),
          interactionSource = interactionSource
        )
      }
    )

    Spacer(modifier = Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      KurobaComposeTextBarButton(
        modifier = Modifier.wrapContentSize(),
        onClick = { stopPresenting() },
        enabled = !blockInput,
        text = stringResource(id = R.string.close)
      )

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeTextBarButton(
        modifier = Modifier.wrapContentSize(),
        onClick = { onLogoutClicked() },
        enabled = !blockInput,
        text = stringResource(id = R.string.login_screen_logout)
      )
    }
  }

  private fun handleLogout(
    onLogoutStart: () -> Unit,
    onLogoutEnd: () -> Unit,
  ) {
    dvachLoginScreenViewModel.logout(
      onLogoutStart = onLogoutStart,
      onLogoutEnd = onLogoutEnd,
      onLogoutResult = { logoutResult ->
        logoutResult
          .toastOnError(
            longToast = true,
            message = { throwable ->
              appResources.string(
                R.string.login_failed_to_log_out,
                throwable.errorMessageOrClassName(userReadable = true)
              )
            }
          )
          .toastOnSuccess(
            message = { appResources.string(R.string.login_successfully_logged_out) }
          )
      }
    )
  }

  private fun handleLogin(
    passcode: String,
    onLoginStart: () -> Unit,
    onLoginEnd: () -> Unit,
  ) {
    dvachLoginScreenViewModel.login(
      passcode = passcode,
      onLoginStart = onLoginStart,
      onLoginEnd = onLoginEnd,
      onLoginResult = { loginResult ->
        loginResult
          .toastOnError(
            longToast = true,
            message = { throwable ->
              appResources.string(
                R.string.login_failed_to_log_in,
                throwable.errorMessageOrClassName(userReadable = true)
              )
            }
          )
          .toastOnSuccess(
            message = { appResources.string(R.string.login_successfully_logged_in) }
          )
      }
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("DvachLoginScreen")

    const val CURRENT_PASSCODE_COOKIE = "current_passcode_cookie"
  }

}