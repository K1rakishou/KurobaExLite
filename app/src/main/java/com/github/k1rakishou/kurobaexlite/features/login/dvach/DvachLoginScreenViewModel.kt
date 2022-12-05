package com.github.k1rakishou.kurobaexlite.features.login.dvach

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.DvachLoginDetails
import com.github.k1rakishou.kurobaexlite.model.data.local.LoginResult
import com.github.k1rakishou.kurobaexlite.sites.dvach.Dvach
import com.github.k1rakishou.kurobaexlite.sites.settings.DvachSiteSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DvachLoginScreenViewModel(
  private val siteManager: SiteManager
) : BaseViewModel() {

  private val _passcodeCookieChangesFlow = MutableStateFlow<String?>(null)
  val passcodeCookieChangesFlow: StateFlow<String?>
    get() = _passcodeCookieChangesFlow.asStateFlow()

  private val dvachSiteSettings by lazy { siteManager.bySiteKey(Dvach.SITE_KEY)!!.siteSettings as DvachSiteSettings }

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    dvachSiteSettings.passcodeCookie.listen()
      .collectLatest { passcodeCookie -> _passcodeCookieChangesFlow.emit(passcodeCookie) }
  }

  fun login(
    passcode: String,
    onLoginStart: () -> Unit,
    onLoginEnd: () -> Unit,
    onLoginResult: (Result<LoginResult>) -> Unit
  ) {
    val loginDataSource = siteManager.bySiteKey(Dvach.SITE_KEY)
      ?.passcodeInfo()
      ?.loginDataSource()
      ?: return

    viewModelScope.launch {
      try {
        onLoginStart()

        val loginResult = loginDataSource.login(DvachLoginDetails(passcode))
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
    val logoutDataSource = siteManager.bySiteKey(Dvach.SITE_KEY)
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