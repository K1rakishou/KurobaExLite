package com.github.k1rakishou.kurobaexlite.features.home

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HomeScreenViewModel(
  application: KurobaExLiteApplication,
  private val siteManager: SiteManager,
  private val snackbarManager: SnackbarManager
) : BaseAndroidViewModel(application) {

  private val _homeScreenFabClickEventFlow = MutableSharedFlow<ScreenKey>(extraBufferCapacity = Channel.UNLIMITED)
  val homeScreenFabClickEventFlow: SharedFlow<ScreenKey>
    get() = _homeScreenFabClickEventFlow.asSharedFlow()

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    return siteManager.resolveDescriptorFromRawIdentifier(rawIdentifier)
  }

  fun onHomeScreenFabClicked(screenKey: ScreenKey) {
    _homeScreenFabClickEventFlow.tryEmit(screenKey)
  }

}