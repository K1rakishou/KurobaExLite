package com.github.k1rakishou.kpnc.ui.main

/*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kpnc.R
import com.github.k1rakishou.kpnc.domain.GoogleServicesChecker
import com.github.k1rakishou.kpnc.helpers.errorMessageOrClassName
import com.github.k1rakishou.kpnc.helpers.isNotNullNorBlank
import com.github.k1rakishou.kpnc.helpers.isUserIdValid
import com.github.k1rakishou.kpnc.model.data.ui.AccountInfo
import com.github.k1rakishou.kpnc.model.data.ui.UiResult
import com.github.k1rakishou.kpnc.ui.theme.KPNCTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {
  private val mainViewModel: MainViewModel by viewModel<MainViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      KPNCTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colors.background
        ) {
          val firebaseToken by mainViewModel.firebaseToken
          val googleServicesCheckResult by mainViewModel.googleServicesCheckResult
          val accountInfo by mainViewModel.accountInfo
          val rememberedUserId by mainViewModel.rememberedUserId
          val rememberedInstanceAddress by mainViewModel.rememberedInstanceAddress

          Box(modifier = Modifier.fillMaxSize()) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
            ) {
              Content(
                googleServicesCheckResult = googleServicesCheckResult,
                firebaseToken = firebaseToken,
                accountInfo = accountInfo,
                rememberedUserId = rememberedUserId,
                rememberedInstanceAddress = rememberedInstanceAddress,
                onLogin = { instanceAddress, userId -> mainViewModel.login(instanceAddress, userId) },
                onLogout = { mainViewModel.logout() }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ColumnScope.Content(
  googleServicesCheckResult: GoogleServicesChecker.Result,
  rememberedUserId: String?,
  rememberedInstanceAddress: String?,
  accountInfo: UiResult<AccountInfo>,
  firebaseToken: UiResult<String>,
  onLogin: (String, String) -> Unit,
  onLogout: () -> Unit
) {
  val context = LocalContext.current

  if (googleServicesCheckResult == GoogleServicesChecker.Result.Empty) {
    Text(text = "Checking for Google services availability...")
    return
  }

  GoogleServices(googleServicesCheckResult)
  Spacer(modifier = Modifier.height(8.dp))

  if (googleServicesCheckResult != GoogleServicesChecker.Result.Success) {
    return
  }

  FirebaseToken(firebaseToken)
  Spacer(modifier = Modifier.height(8.dp))

  AccountId(accountInfo)
  Spacer(modifier = Modifier.height(8.dp))

  var userId by remember { mutableStateOf(rememberedUserId ?: "") }
  var instanceAddress by remember { mutableStateOf(rememberedInstanceAddress ?: "") }
  var isError by remember { mutableStateOf(!isUserIdValid(userId)) }
  val isLoggedIn = ((accountInfo as? UiResult.Value)?.value?.isValid == true) || isUserIdValid(rememberedUserId)

  TextField(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    enabled = !isLoggedIn,
    label = { Text(text = "Instance address") },
    value = instanceAddress,
    onValueChange = {
      instanceAddress = it
    }
  )

  Spacer(modifier = Modifier.height(16.dp))

  TextField(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    enabled = !isLoggedIn,
    label = { Text(text = "UserId (${userId.length}/128)") },
    isError = isError,
    value = userId,
    onValueChange = {
      userId = it
      isError = !isUserIdValid(userId)

      if (isError) {
        showToast(context, "UserId length must be within 32..128 characters range")
      }
    }
  )

  Spacer(modifier = Modifier.height(8.dp))

  AccountInfo(accountInfo, context)

  Spacer(modifier = Modifier.height(4.dp))

  val buttonEnabled = if (isLoggedIn) {
    true
  } else {
    isUserIdValid(userId) && instanceAddress.isNotNullNorBlank() && accountInfo !is UiResult.Loading
  }

  Row {
    Button(
      enabled = buttonEnabled,
      onClick = {
        if (isLoggedIn) {
          userId = ""
          onLogout()
        } else {
          onLogin(instanceAddress, userId)
        }
      }
    ) {
      if (isLoggedIn) {
        Text(text = "Logout")
      } else {
        Text(text = "Login")
      }
    }
  }
}

@Composable
private fun AccountInfo(
  accountInfo: UiResult<AccountInfo>,
  context: Context
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
        Text(
          modifier = Modifier.weight(1f),
          text = "Not logged in"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )
      }
      is UiResult.Value -> {
        Text(
          modifier = Modifier.weight(1f),
          text = accountInfo.value.asText()
        )

        Spacer(modifier = Modifier.width(8.dp))

        val drawableId = if (accountInfo.value.isValid) {
          R.drawable.baseline_check_circle_outline_24
        } else {
          R.drawable.baseline_warning_amber_24
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
        Text(
          modifier = Modifier.weight(1f),
          text = accountInfo.throwable.errorMessageOrClassName(userReadable = true)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )

        LaunchedEffect(
          key1 = accountInfo.throwable,
          block = {
            showToast(
              context = context,
              message = accountInfo.throwable.errorMessageOrClassName(userReadable = true)
            )
          }
        )
      }
    }
  }
}

@Composable
private fun GoogleServices(googleServicesCheckResult: GoogleServicesChecker.Result) {
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
    Text(
      modifier = Modifier.weight(1f),
      text = googleServicesCheckResultText
    )

    Spacer(modifier = Modifier.width(8.dp))

    val drawableId = if (googleServicesCheckResult == GoogleServicesChecker.Result.Success) {
      R.drawable.baseline_check_circle_outline_24
    } else {
      R.drawable.baseline_warning_amber_24
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
fun AccountId(accountInfo: UiResult<AccountInfo>) {
  val context = LocalContext.current

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
        Text(text = "Loading account info...")
      }
      is UiResult.Error -> {
        Text(
          modifier = Modifier.weight(1f),
          text = "Failed to load account, " +
            "error: ${accountInfo.throwable.errorMessageOrClassName(userReadable = true)}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )
      }
      else -> {
        accountInfo as UiResult.Value

        Text(
          modifier = Modifier
            .weight(1f)
            .clickable { context.copyToClipboard("AccountId", accountInfo.value.accountId) },
          text = "Account id: ${accountInfo.value.accountId}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.baseline_check_circle_outline_24,
          iconStatus = IconStatus.Success
        )
      }
    }
  }
}

@Composable
private fun FirebaseToken(firebaseToken: UiResult<String>) {
  val context = LocalContext.current

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    when (firebaseToken) {
      is UiResult.Empty,
      is UiResult.Loading -> {
        Text(text = "Loading firebase token...")
      }
      is UiResult.Error -> {
        Text(
          modifier = Modifier.weight(1f),
          text = "Failed to load firebase token, " +
            "error: ${firebaseToken.throwable.errorMessageOrClassName(userReadable = true)}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.baseline_warning_amber_24,
          iconStatus = IconStatus.Error
        )
      }
      else -> {
        firebaseToken as UiResult.Value

        Text(
          modifier = Modifier
            .weight(1f)
            .clickable { context.copyToClipboard("FirebaseToken", firebaseToken.value) },
          text = "Firebase token: ${firebaseToken.value}"
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          drawableId = R.drawable.baseline_check_circle_outline_24,
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

private fun Context.copyToClipboard(label: String, content: String) {
  val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboardManager.setPrimaryClip(ClipData.newPlainText(label, content))

  showToast(this, "Content copied to clipboard")
}

enum class IconStatus {
  Error,
  Success
}

private var prevToast: Toast? = null

private fun showToast(
  context: Context,
  message: String
) {
  prevToast?.cancel()

  val toast = Toast.makeText(
    context,
    message,
    Toast.LENGTH_LONG
  )

  prevToast = toast
  toast.show()
}
 */