package com.github.k1rakishou.kurobaexlite.features.home

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.features.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import logcat.logcat

class HomeScreenViewModel(
  application: KurobaExLiteApplication,
  private val siteManager: SiteManager
) : BaseAndroidViewModel(application) {

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    return siteManager.resolveDescriptorFromRawIdentifier(rawIdentifier)
  }

  fun onFabClicked(screenKey: ScreenKey) {
    logcat { "onFabClicked($screenKey)" }
  }

}