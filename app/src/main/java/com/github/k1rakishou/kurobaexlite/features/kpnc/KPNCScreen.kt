package com.github.k1rakishou.kurobaexlite.features.kpnc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kpnc.domain.GoogleServicesChecker
import com.github.k1rakishou.kpnc.helpers.isUserIdValid
import com.github.k1rakishou.kpnc.model.data.ui.AccountInfo
import com.github.k1rakishou.kpnc.model.data.ui.UiResult
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.viewmodel.ext.android.viewModel

class KPNCScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<KPNCScreen.ToolbarIcons>>(screenArgs, componentActivity, navigationRouter) {
  private val kpncScreenViewModel by componentActivity.viewModel<KPNCScreenViewModel>()

  override val screenKey: ScreenKey = SCREEN_KEY
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)
  override val hasFab: Boolean = false

  private val defaultToolbarKey = "${screenKey.key}_toolbar"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .titleId(R.string.kpnc_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
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
      modifier = Modifier.fillMaxSize()
    ) {
      KurobaComposeFadeIn {
        ScreenContentInternal(
          kpncScreenViewModel = kpncScreenViewModel
        )
      }
    }
  }

  enum class ToolbarIcons {
    Back,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("KPNCScreen")
  }

}

private const val TOAST_ID = "kpnc_screen_toast"
private const val ERROR_TOAST_ID = "kpnc_screen_error_toast"

@Composable
private fun ScreenContentInternal(
  kpncScreenViewModel: KPNCScreenViewModel
) {
  val insets = LocalWindowInsets.current
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  val firebaseToken by kpncScreenViewModel.firebaseToken
  val googleServicesCheckResult by kpncScreenViewModel.googleServicesCheckResult
  val accountInfo by kpncScreenViewModel.accountInfo
  val rememberedUserId by kpncScreenViewModel.rememberedUserId
  val rememberedInstanceAddress by kpncScreenViewModel.rememberedInstanceAddress

  val snackbarManager = koinRemember<SnackbarManager>()
  val androidHelpers = koinRemember<AndroidHelpers>()

  val scrollState = rememberScrollState()
  val additionalPadding = 8.dp
  val fontSize = 14.sp

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(
        top = insets.top + toolbarHeight + additionalPadding,
        bottom = insets.bottom + additionalPadding,
        start = additionalPadding,
        end = additionalPadding
      )
      .verticalScroll(state = scrollState)
  ) {
    Content(
      fontSize = fontSize,
      googleServicesCheckResult = googleServicesCheckResult,
      firebaseToken = firebaseToken,
      accountInfo = accountInfo,
      rememberedUserId = rememberedUserId,
      rememberedInstanceAddress = rememberedInstanceAddress,
      showToast = { isError, message ->
        if (isError) {
          snackbarManager.errorToast(
            message = message,
            toastId = ERROR_TOAST_ID
          )
        } else {
          snackbarManager.toast(
            message = message,
            toastId = TOAST_ID
          )
        }
      },
      hideToast = { isError ->
        val toastId = if (isError) {
          ERROR_TOAST_ID
        } else {
          TOAST_ID
        }

        snackbarManager.hideToast(toastId)
      },
      copyToClipboard = { label, content ->
        androidHelpers.copyToClipboard(label, content)
        snackbarManager.toast(
          message = "${label} copied to clipboard",
          toastId = TOAST_ID
        )
      },
      onLogin = { instanceAddress, userId -> kpncScreenViewModel.login(instanceAddress, userId) },
      onLogout = { kpncScreenViewModel.logout() }
    )
  }
}

@Composable
private fun Content(
  fontSize: TextUnit,
  googleServicesCheckResult: GoogleServicesChecker.Result,
  rememberedUserId: String?,
  rememberedInstanceAddress: String?,
  accountInfo: UiResult<AccountInfo>,
  firebaseToken: UiResult<String>,
  showToast: (Boolean, String) -> Unit,
  hideToast: (Boolean) -> Unit,
  copyToClipboard: (String, String) -> Unit,
  onLogin: (String, String) -> Unit,
  onLogout: () -> Unit
) {
  if (googleServicesCheckResult == GoogleServicesChecker.Result.Empty) {
    KurobaComposeText(text = "Checking for Google services availability...")
    return
  }

  GoogleServices(
    fontSize = fontSize,
    googleServicesCheckResult = googleServicesCheckResult
  )

  Spacer(modifier = Modifier.height(8.dp))

  if (googleServicesCheckResult != GoogleServicesChecker.Result.Success) {
    return
  }

  FirebaseToken(
    fontSize = fontSize,
    firebaseToken = firebaseToken,
    copyToClipboard = copyToClipboard
  )

  Spacer(modifier = Modifier.height(8.dp))

  AccountId(
    fontSize = fontSize,
    accountInfo = accountInfo,
    copyToClipboard = copyToClipboard
  )

  Spacer(modifier = Modifier.height(8.dp))

  var userId by remember { mutableStateOf(TextFieldValue(text = rememberedUserId ?: "")) }
  var instanceAddress by remember { mutableStateOf(TextFieldValue(text = rememberedInstanceAddress ?: "")) }
  var isError by remember { mutableStateOf(!isUserIdValid(userId.text)) }
  val isLoggedIn = (accountInfo as? UiResult.Value)?.value?.isValid == true

  KurobaComposeTextField(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    enabled = !isLoggedIn,
    label = { Text(text = "Instance address") },
    value = instanceAddress,
    onValueChange = { instanceAddress = it }
  )

  Spacer(modifier = Modifier.height(16.dp))

  KurobaComposeTextField(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    enabled = !isLoggedIn,
    label = { Text(text = "UserId (${userId.text.length}/128)") },
    isError = isError,
    value = userId,
    onValueChange = {
      userId = it
      isError = !isUserIdValid(userId.text)

      if (isError) {
        showToast(true, "UserId length must be within 32..128 characters range")
      } else {
        hideToast(true)
      }
    }
  )

  Spacer(modifier = Modifier.height(8.dp))

  AccountInfo(
    fontSize = fontSize,
    accountInfo = accountInfo,
    showToast = showToast
  )

  Spacer(modifier = Modifier.height(4.dp))

  val buttonEnabled = if (isLoggedIn) {
    true
  } else {
    isUserIdValid(userId.text) && instanceAddress.text.isNotNullNorBlank() && accountInfo !is UiResult.Loading
  }

  Row {
    KurobaComposeTextButton(
      enabled = buttonEnabled,
      text = if (isLoggedIn) {
        "Logout"
      } else {
        "Login"
      },
      onClick = {
        if (isLoggedIn) {
          userId = TextFieldValue()
          onLogout()
        } else {
          onLogin(instanceAddress.text, userId.text)
        }
      }
    )
  }
}

@Composable
private fun AccountInfo(
  fontSize: TextUnit,
  accountInfo: UiResult<AccountInfo>,
  showToast: (Boolean, String) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    when (accountInfo) {
      is UiResult.Loading -> {
        // no-op
      }
      is UiResult.Empty -> {
        KurobaComposeText(
          modifier = Modifier.weight(1f),
          fontSize = fontSize,
          text = "Not logged in"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.ic_baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )
      }
      is UiResult.Value -> {
        KurobaComposeText(
          modifier = Modifier.weight(1f),
          fontSize = fontSize,
          text = accountInfo.value.asText()
        )

        Spacer(modifier = Modifier.width(8.dp))

        val drawableId = if (accountInfo.value.isValid) {
          R.drawable.ic_baseline_check_circle_outline_24
        } else {
          R.drawable.ic_baseline_warning_amber_24
        }

        val iconStatus = if (accountInfo.value.isValid) {
          IconStatus.Success
        } else {
          IconStatus.Error
        }

        Icon(
          drawableId = drawableId,
          iconStatus = iconStatus
        )
      }
      is UiResult.Error -> {
        KurobaComposeText(
          modifier = Modifier.weight(1f),
          fontSize = fontSize,
          text = accountInfo.throwable.errorMessageOrClassName(userReadable = true)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.ic_baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )

        LaunchedEffect(
          key1 = accountInfo.throwable,
          block = {
            showToast(true, accountInfo.throwable.errorMessageOrClassName(userReadable = true))
          }
        )
      }
    }
  }
}

@Composable
private fun GoogleServices(
  fontSize: TextUnit,
  googleServicesCheckResult: GoogleServicesChecker.Result
) {
  val googleServicesCheckResultText = when (googleServicesCheckResult) {
    GoogleServicesChecker.Result.Empty -> return
    GoogleServicesChecker.Result.Success -> "Google services detected"
    GoogleServicesChecker.Result.ServiceMissing -> "Google services are missing"
    GoogleServicesChecker.Result.ServiceUpdating -> "Google services are currently updating"
    GoogleServicesChecker.Result.ServiceUpdateRequired -> "Google services need to be updated"
    GoogleServicesChecker.Result.ServiceDisabled -> "Google services are disabled"
    GoogleServicesChecker.Result.ServiceInvalid -> "Google services are not working correctly"
    GoogleServicesChecker.Result.Unknown -> "Google services unknown error"
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    KurobaComposeText(
      modifier = Modifier.weight(1f),
      fontSize = fontSize,
      text = googleServicesCheckResultText
    )

    Spacer(modifier = Modifier.width(8.dp))

    val drawableId = if (googleServicesCheckResult == GoogleServicesChecker.Result.Success) {
      R.drawable.ic_baseline_check_circle_outline_24
    } else {
      R.drawable.ic_baseline_warning_amber_24
    }

    val iconStatus = if (googleServicesCheckResult == GoogleServicesChecker.Result.Success) {
      IconStatus.Success
    } else {
      IconStatus.Error
    }

    Icon(
      drawableId = drawableId,
      iconStatus = iconStatus
    )
  }
}

@Composable
fun AccountId(
  fontSize: TextUnit,
  accountInfo: UiResult<AccountInfo>,
  copyToClipboard: (String, String) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    when (accountInfo) {
      is UiResult.Empty -> {
        // no-op
      }
      is UiResult.Loading -> {
        KurobaComposeText(
          fontSize = fontSize,
          text = "Loading account info..."
        )
      }
      is UiResult.Error -> {
        KurobaComposeText(
          modifier = Modifier.weight(1f),
          fontSize = fontSize,
          text = "Failed to load account, " +
            "error: ${accountInfo.throwable.errorMessageOrClassName(userReadable = true)}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.ic_baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )
      }
      else -> {
        accountInfo as UiResult.Value

        KurobaComposeText(
          modifier = Modifier
            .weight(1f)
            .clickable { copyToClipboard("AccountId", accountInfo.value.accountId) },
          fontSize = fontSize,
          text = "Account id: ${accountInfo.value.accountId}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.ic_baseline_check_circle_outline_24,
          iconStatus = IconStatus.Success
        )
      }
    }
  }
}

@Composable
private fun FirebaseToken(
  fontSize: TextUnit,
  firebaseToken: UiResult<String>,
  copyToClipboard: (String, String) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    when (firebaseToken) {
      is UiResult.Empty,
      is UiResult.Loading -> {
        KurobaComposeText(
          fontSize = fontSize,
          text = "Loading firebase token..."
        )
      }
      is UiResult.Error -> {
        KurobaComposeText(
          modifier = Modifier.weight(1f),
          fontSize = fontSize,
          text = "Failed to load firebase token, " +
            "error: ${firebaseToken.throwable.errorMessageOrClassName(userReadable = true)}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.ic_baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )
      }
      else -> {
        firebaseToken as UiResult.Value

        KurobaComposeText(
          modifier = Modifier
            .weight(1f)
            .clickable { copyToClipboard("FirebaseToken", firebaseToken.value) },
          fontSize = fontSize,
          text = "Firebase token: ${firebaseToken.value}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.ic_baseline_check_circle_outline_24,
          iconStatus = IconStatus.Success
        )
      }
    }
  }
}

@Composable
private fun Icon(
  drawableId: Int,
  iconStatus: IconStatus
) {
  Image(
    modifier = Modifier
      .size(36.dp)
      .drawBehind {
        when (iconStatus) {
          IconStatus.Error -> drawCircle(color = Color.Red.copy(alpha = 0.8f))
          IconStatus.Success -> drawCircle(color = Color.Green.copy(alpha = 0.8f))
        }
      }
      .padding(4.dp),
    painter = painterResource(id = drawableId),
    contentDescription = null
  )
}

private enum class IconStatus {
  Error,
  Success
}
