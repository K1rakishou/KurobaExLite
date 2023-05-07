package com.github.k1rakishou.kurobaexlite.features.kpnc


import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kpnc.AppConstants
import com.github.k1rakishou.kpnc.domain.GoogleServicesChecker
import com.github.k1rakishou.kpnc.domain.TokenUpdater
import com.github.k1rakishou.kpnc.helpers.retrieveFirebaseToken
import com.github.k1rakishou.kpnc.model.data.ui.AccountInfo
import com.github.k1rakishou.kpnc.model.data.ui.UiResult
import com.github.k1rakishou.kpnc.model.repository.AccountRepository
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatDebug
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.model.ClientException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime

class KPNCScreenViewModel(
  private val sharedPrefs: SharedPreferences,
  private val googleServicesChecker: GoogleServicesChecker,
  private val tokenUpdater: TokenUpdater,
  private val accountRepository: AccountRepository,
) : ViewModel() {
  private val _rememberedUserId = mutableStateOf<String?>(null)
  val rememberedUserId: State<String?>
    get() = _rememberedUserId
  private val _rememberedInstanceAddress = mutableStateOf<String?>(null)
  val rememberedInstanceAddress: State<String?>
    get() = _rememberedInstanceAddress
  
  private val _googleServicesCheckResult = mutableStateOf<GoogleServicesChecker.Result>(GoogleServicesChecker.Result.Empty)
  val googleServicesCheckResult: State<GoogleServicesChecker.Result>
    get() = _googleServicesCheckResult

  private val _firebaseToken = mutableStateOf<UiResult<String>>(UiResult.Empty)
  val firebaseToken: State<UiResult<String>>
    get() = _firebaseToken

  private val _accountInfo = mutableStateOf<UiResult<AccountInfo>>(UiResult.Empty)
  val accountInfo: State<UiResult<AccountInfo>>
    get() = _accountInfo

  init {
    viewModelScope.launch {
      val result = googleServicesChecker.checkGoogleServicesStatus()
      _googleServicesCheckResult.value = result

      if (result != GoogleServicesChecker.Result.Success) {
        return@launch
      }

      _firebaseToken.value = UiResult.Loading
      _rememberedUserId.value = sharedPrefs.getString(AppConstants.PrefKeys.USER_ID, null)
        ?.takeIf { it.isNotBlank() }
      _rememberedInstanceAddress.value = sharedPrefs.getString(AppConstants.PrefKeys.INSTANCE_ADDRESS, null)
        ?.takeIf { it.isNotBlank() }

      retrieveFirebaseToken()
        .onFailure { error -> _firebaseToken.value = UiResult.Error(error) }
        .onSuccess { token ->
          sharedPrefs.edit { putString(AppConstants.PrefKeys.TOKEN, token) }
          _firebaseToken.value = UiResult.Value(token)
        }
    }

    val instanceAddress = _rememberedInstanceAddress.value
    val userId = _rememberedUserId.value

    if (instanceAddress.isNotNullNorBlank() && userId.isNotNullNorBlank()) {
      viewModelScope.launch {
        val accountInfoResult = accountRepository.getAccountInfo(instanceAddress, userId)
        if (accountInfoResult.isFailure) {
          _accountInfo.value = UiResult.Error(accountInfoResult.exceptionOrNull()!!)
        } else {
          val accountInfoResponse = accountInfoResult.unwrap()

          val accountInfo = AccountInfo(
            accountId = accountInfoResponse.accountId,
            isValid = accountInfoResponse.isValid,
            validUntil = DateTime(accountInfoResponse.validUntil),
          )
          _accountInfo.value = UiResult.Value(accountInfo)
        }
      }
    }
  }

  fun login(instanceAddress: String, userId: String) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        _accountInfo.value = UiResult.Loading

        var firebaseToken = sharedPrefs.getString(AppConstants.PrefKeys.TOKEN, null)
        if (firebaseToken.isNullOrEmpty()) {
          retrieveFirebaseToken()
            .onFailure { error -> _firebaseToken.value = UiResult.Error(error) }
            .onSuccess { token ->
              sharedPrefs.edit { putString(AppConstants.PrefKeys.TOKEN, token) }
              _firebaseToken.value = UiResult.Value(token)
            }

          firebaseToken = sharedPrefs.getString(AppConstants.PrefKeys.TOKEN, null)
          if (firebaseToken.isNullOrEmpty()) {
            if (_accountInfo.value !is UiResult.Error) {
              _accountInfo.value = UiResult.Error(KPNCException("Failed to retrieve FirebaseToken"))
            }

            return@withContext
          }
        }

        try {
          val tokenUpdateResult = tokenUpdater.updateToken(instanceAddress, userId, firebaseToken)
            .onFailure { error ->
              logcatError(TAG) { "tokenUpdater.updateToken() " +
                "error: ${error.asLogIfImportantOrErrorMessage()}" }
            }

          val tokenUpdated = if (tokenUpdateResult.isFailure) {
            logcatError(TAG) {
              "updateFirebaseToken() updateToken() " +
                "error: ${tokenUpdateResult.exceptionOrNull()!!.errorMessageOrClassName()}"
            }

            _accountInfo.value = UiResult.Error(tokenUpdateResult.exceptionOrNull()!!)
            return@withContext
          } else {
            tokenUpdateResult.getOrThrow()
          }

          if (!tokenUpdated) {
            logcatError(TAG) { "updateFirebaseToken() updateToken() returned false" }
            _accountInfo.value = UiResult.Error(tokenUpdateResult.exceptionOrNull()!!)
            return@withContext
          }

          val accountInfoResult = accountRepository.getAccountInfo(instanceAddress, userId)
          if (accountInfoResult.isFailure) {
            logcatError(TAG) {
              "updateFirebaseToken() error: " +
                "${accountInfoResult.exceptionOrNull()?.asLogIfImportantOrErrorMessage()}"
            }

            _accountInfo.value = UiResult.Error(accountInfoResult.exceptionOrNull()!!)
            return@withContext
          }

          val accountInfoResponse = accountInfoResult.getOrThrow()

          val accountInfo = AccountInfo(
            accountId = accountInfoResponse.accountId,
            isValid = accountInfoResponse.isValid,
            validUntil = DateTime(accountInfoResponse.validUntil),
          )

          sharedPrefs.edit {
            putString(AppConstants.PrefKeys.USER_ID, userId)
            putString(AppConstants.PrefKeys.INSTANCE_ADDRESS, instanceAddress)
          }
          _accountInfo.value = UiResult.Value(accountInfo)

          logcatDebug(TAG) { "updateFirebaseToken() success" }
        } catch (error: Throwable) {
          logcatDebug(TAG) { "updateFirebaseToken() error: ${error.asLogIfImportantOrErrorMessage()}" }
          _accountInfo.value = UiResult.Error(error)
        }
      }
    }
  }

  fun logout() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        sharedPrefs.edit {
          remove(AppConstants.PrefKeys.TOKEN)
          remove(AppConstants.PrefKeys.USER_ID)
        }

        _accountInfo.value = UiResult.Empty
      }
    }
  }

  class KPNCException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "MainViewModel"
  }

}