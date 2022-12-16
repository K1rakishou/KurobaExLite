package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.CaptchaManager
import com.github.k1rakishou.kurobaexlite.managers.CatalogManager
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.managers.FastScrollerMarksManager
import com.github.k1rakishou.kurobaexlite.managers.FirewallBypassManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.ISiteManager
import com.github.k1rakishou.kurobaexlite.managers.LastVisitedEndpointManager
import com.github.k1rakishou.kurobaexlite.managers.MarkedPostManager
import com.github.k1rakishou.kurobaexlite.managers.NavigationHistoryManager
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.ReportManager
import com.github.k1rakishou.kurobaexlite.managers.RevealedSpoilerImages
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UpdateManager
import org.koin.core.module.Module

internal fun Module.managers() {
  single { SiteManager(siteProvider = get()) }
  single<ISiteManager> { get<SiteManager>() }
  single { ApplicationVisibilityManager() }
  single { CatalogManager() }
  single { PostReplyChainManager() }
  single { ChanViewManager() }
  single { SnackbarManager(appContext = get()) }
  single { NavigationHistoryManager() }
  single { MarkedPostManager() }
  single { CaptchaManager() }
  single { LastVisitedEndpointManager(appScope = get(), appSettings = get()) }
  single { BookmarksManager() }

  single {
    ChanThreadManager(
      siteManager = get(),
      chanCache = get(),
      parsedPostDataCache = get()
    )
  }

  single {
    UpdateManager(
      appContext = get(),
      notificationManagerCompat = get(),
      appScope = get(),
      appSettings = get(),
      androidHelpers = get(),
      proxiedOkHttpClient = get(),
      moshi = get()
    )
  }

  single {
    GlobalUiInfoManager(
      appScope = get(),
      appResources = get(),
      appSettings = get()
    )
  }

  single(createdAtStart = true) {
    FastScrollerMarksManager(
      appScope = get(),
      appSettings = get(),
      parsedPostDataCache = get(),
      chanCache = get()
    )
  }

  single {
    FirewallBypassManager(
      appScope = get(),
      applicationVisibilityManager = get()
    )
  }

  single {
    ReportManager(
      appScope = get(),
      appContext = get(),
      androidHelpers = get(),
      proxiedOkHttpClient = get(),
      moshi = get(),
    )
  }

  single {
    RevealedSpoilerImages()
  }
}