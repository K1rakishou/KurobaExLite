package com.github.k1rakishou.kurobaexlite.features.home

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import logcat.logcat

class HomeScreenViewModel(
  application: KurobaExLiteApplication,
  private val siteManager: SiteManager,
  private val snackbarManager: SnackbarManager
) : BaseAndroidViewModel(application) {

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    return siteManager.resolveDescriptorFromRawIdentifier(rawIdentifier)
  }

  fun onFabClicked(screenKey: ScreenKey) {
    logcat { "onFabClicked($screenKey)" }
    snackbarManager.toast("FAB clicked ${screenKey}")
  }

}