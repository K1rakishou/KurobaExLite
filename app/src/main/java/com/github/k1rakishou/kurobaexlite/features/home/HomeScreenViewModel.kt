package com.github.k1rakishou.kurobaexlite.features.home

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.managers.CaptchaManager
import com.github.k1rakishou.kurobaexlite.managers.CaptchaRequest
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

class HomeScreenViewModel(
  application: KurobaExLiteApplication,
  private val siteManager: SiteManager,
  private val captchaManager: CaptchaManager
) : BaseAndroidViewModel(application) {

  private val _homeScreenFabClickEventFlow = MutableSharedFlow<ScreenKey>(extraBufferCapacity = Channel.UNLIMITED)
  val homeScreenFabClickEventFlow: SharedFlow<ScreenKey>
    get() = _homeScreenFabClickEventFlow.asSharedFlow()

  val captchaRequestsFlow: SharedFlow<Pair<CaptchaRequest, SiteCaptcha>>
    get() {
      return captchaManager.captchaRequestsFlow
        .mapNotNull { capchaRequest ->
          val siteCaptcha = siteManager.bySiteKey(capchaRequest.chanDescriptor.siteKey)?.siteCaptcha
          if (siteCaptcha == null) {
            return@mapNotNull null
          }

          return@mapNotNull capchaRequest to siteCaptcha
        }
        .shareIn(viewModelScope, SharingStarted.Lazily)
    }

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    return siteManager.resolveDescriptorFromRawIdentifier(rawIdentifier)
  }

  fun onHomeScreenFabClicked(screenKey: ScreenKey) {
    _homeScreenFabClickEventFlow.tryEmit(screenKey)
  }

}