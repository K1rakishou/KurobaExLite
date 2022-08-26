package com.github.k1rakishou.kurobaexlite.features.login

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
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextFieldLabel
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class Chan4LoginScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val chan4LoginScreenViewModel: Chan4LoginScreenViewModel by componentActivity.viewModel()

  private val passcodeCookieArg by requireArgumentLazy<String>(CURRENT_PASSCODE_COOKIE)

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    super.FloatingContent()

    val currentPasscodeCookieMut by chan4LoginScreenViewModel.passcodeCookieChangesFlow.collectAsState()
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
        TokenWithPinInputs(
          blockInput = blockInput,
          onLoginClicked = { token, pin ->
            handleLogin(
              token = token,
              pin = pin,
              onLoginStart = { blockInput = true },
              onLoginEnd = { blockInput = false },
            )
          }
        )
      }
    }
  }

  @Composable
  private fun TokenWithPinInputs(
    blockInput: Boolean,
    onLoginClicked: (String, String) -> Unit
  ) {
    var token by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    KurobaComposeTextField(
      modifier = Modifier.fillMaxWidth(),
      value = token,
      enabled = !blockInput,
      onValueChange = { value -> token = value },
      label = { KurobaComposeTextFieldLabel(R.string.chan4_setting_passcode_token) }
    )

    Spacer(modifier = Modifier.height(12.dp))

    KurobaComposeTextField(
      modifier = Modifier.fillMaxWidth(),
      value = pin,
      enabled = !blockInput,
      onValueChange = { value -> pin = value },
      label = { KurobaComposeTextFieldLabel(R.string.chan4_setting_passcode_pin) }
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
        enabled = !blockInput && token.isNotEmpty() && pin.isNotEmpty(),
        onClick = { onLoginClicked(token, pin) },
        text = stringResource(id = R.string.chan4_login_screen_login)
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
      value = currentPasscodeCookie,
      enabled = !blockInput,
      onValueChange = { },
      label = { KurobaComposeTextFieldLabel(R.string.chan4_setting_passcode_cookie) }
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
        text = stringResource(id = R.string.chan4_login_screen_logout)
      )
    }
  }

  private fun handleLogout(
    onLogoutStart: () -> Unit,
    onLogoutEnd: () -> Unit,
  ) {
    chan4LoginScreenViewModel.logout(
      onLogoutStart = onLogoutStart,
      onLogoutEnd = onLogoutEnd,
      onLogoutResult = { logoutResult ->
        logoutResult
          .toastOnError(
            longToast = true,
            message = { throwable ->
              appResources.string(
                R.string.chan4_login_failed_to_log_out,
                throwable.errorMessageOrClassName()
              )
            }
          )
          .toastOnSuccess(
            message = { appResources.string(R.string.chan4_login_successfully_logged_out) }
          )
      }
    )
  }

  private fun handleLogin(
    token: String,
    pin: String,
    onLoginStart: () -> Unit,
    onLoginEnd: () -> Unit,
  ) {
    chan4LoginScreenViewModel.login(
      token = token,
      pin = pin,
      onLoginStart = onLoginStart,
      onLoginEnd = onLoginEnd,
      onLoginResult = { loginResult ->
        loginResult
          .toastOnError(
            longToast = true,
            message = { throwable ->
              appResources.string(
                R.string.chan4_login_failed_to_log_in,
                throwable.errorMessageOrClassName()
              )
            }
          )
          .toastOnSuccess(
            message = { appResources.string(R.string.chan4_login_successfully_logged_in) }
          )
      }
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("LoginScreen")

    const val CURRENT_PASSCODE_COOKIE = "current_passcode_cookie"
  }

}