package com.github.k1rakishou.kurobaexlite.features.login

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.Chan4LoginDetails
import com.github.k1rakishou.kurobaexlite.model.data.local.Chan4LoginResult
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import com.github.k1rakishou.kurobaexlite.sites.settings.Chan4SiteSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class Chan4LoginScreenViewModel(
  private val siteManager: SiteManager
) : BaseViewModel() {

  private val _passcodeCookieChangesFlow = MutableStateFlow<String?>(null)
  val passcodeCookieChangesFlow: StateFlow<String?>
    get() = _passcodeCookieChangesFlow.asStateFlow()

  private val chan4SiteSettings by lazy { siteManager.bySiteKey(Chan4.SITE_KEY)!!.siteSettings as Chan4SiteSettings }

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    chan4SiteSettings.passcodeCookie.listen()
      .collectLatest { passcodeCookie -> _passcodeCookieChangesFlow.emit(passcodeCookie) }
  }

  fun login(
    token: String,
    pin: String,
    onLoginStart: () -> Unit,
    onLoginEnd: () -> Unit,
    onLoginResult: (Result<Chan4LoginResult>) -> Unit
  ) {
    val loginDataSource = siteManager.bySiteKey(Chan4.SITE_KEY)
      ?.passcodeInfo()
      ?.loginDataSource()
      ?: return

    viewModelScope.launch {
      try {
        onLoginStart()

        val chan4LoginDetails = Chan4LoginDetails(token, pin)
        val loginResult = loginDataSource.login(chan4LoginDetails)
        onLoginResult(loginResult)
      } finally {
        onLoginEnd()
      }
    }
  }

  fun logout(
    onLogoutStart: () -> Unit,
    onLogoutEnd: () -> Unit,
    onLogoutResult: (Result<Unit>
  ) -> Unit) {
    val logoutDataSource = siteManager.bySiteKey(Chan4.SITE_KEY)
      ?.passcodeInfo()
      ?.logoutDataSource()
      ?: return

    viewModelScope.launch {
      try {
        onLogoutStart()

        val logoutResult = logoutDataSource.logout(Unit)
        onLogoutResult(logoutResult)
      } finally {
        onLogoutEnd()
      }
    }
  }

}