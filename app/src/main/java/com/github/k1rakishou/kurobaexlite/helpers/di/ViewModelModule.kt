package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.chan.core.mpv.MpvInitializer
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.boards.CatalogSelectionScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.captcha.chan4.Chan4CaptchaViewModel
import com.github.k1rakishou.kurobaexlite.features.captcha.dvach.DvachCaptchaScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.downloads.DownloadsScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.drawer.BookmarksScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.drawer.HistoryScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.kpnc.KPNCScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.login.chan4.Chan4LoginScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.login.dvach.DvachLoginScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.DefaultPopupPostsScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.MediaViewerPopupPostsScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.search.global.GlobalSearchScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.search.image.RemoteImageSearchScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.PostCellIconViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.features.screenshot.PostScreenshotScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.settings.application.AppSettingsScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.themes.ThemesScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.activity.MainActivityViewModel
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.AnimateableStackContainerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module

internal fun Module.viewModels() {
  viewModel {
    MainActivityViewModel(
      loadBookmarks = get(),
      snackbarManager = get(),
      appResources = get(),
    )
  }

  viewModel {
    HomeScreenViewModel(
      siteManager = get(),
      captchaManager = get()
    )
  }
  viewModel {
    CatalogScreenViewModel(
      savedStateHandle = get(),
      updateChanCatalogView = get(),
      lastVisitedEndpointManager = get(),
      loadNavigationHistory = get(),
    )
  }
  viewModel {
    ThreadScreenViewModel(
      savedStateHandle = get(),
      loadChanThreadView = get(),
      updateChanThreadView = get(),
      crossThreadFollowHistory = get(),
      lastVisitedEndpointManager = get(),
      loadNavigationHistory = get(),
      addOrRemoveBookmark = get(),
      updatePostSeenForBookmark = get(),
      catalogPagesRepository = get(),
      applicationVisibilityManager = get()
    )
  }

  viewModel {
    AlbumScreenViewModel(
      appSettings = get(),
      appResources = get(),
      chanViewManager = get(),
      parsedPostDataRepository = get(),
      postHideRepository = get(),
      chanPostCache = get(),
      mediaSaver = get(),
    )
  }

  viewModel { DefaultPopupPostsScreenViewModel(savedStateHandle = get()) }
  viewModel { MediaViewerPopupPostsScreenViewModel(savedStateHandle = get()) }
  viewModel {
    CatalogSelectionScreenViewModel(
      retrieveSiteCatalogList = get(),
      loadCatalogsForAllSites = get()
    )
  }
  viewModel {
    val mpvSettings = MpvSettings(
      appContext = get(),
      androidHelpers = get()
    )

    val mpvInitializer = MpvInitializer(
      applicationContext = get(),
      androidHelpers = get(),
      mpvSettings = mpvSettings
    )

    MediaViewerScreenViewModel(
      savedStateHandle = get(),
      mpvSettings = mpvSettings,
      mpvInitializer = mpvInitializer,
      appSettings = get(),
      appResources = get(),
      chanPostCache = get(),
      postHideRepository = get(),
      siteManager = get(),
      proxiedOkHttpClient = get(),
      kurobaLruDiskCache = get(),
      installMpvNativeLibrariesFromGithub = get(),
      imageLoader = get(),
      mediaSaver = get(),
      postReplyChainRepository = get(),
      revealedSpoilerImages = get()
    )
  }

  viewModel {
    HistoryScreenViewModel(
      siteManager = get(),
      appSettings = get(),
      loadNavigationHistory = get(),
      modifyNavigationHistory = get(),
      persistNavigationHistory = get(),
      navigationHistoryManager = get(),
    )
  }

  viewModel {
    ReplyLayoutViewModel(
      captchaManager = get(),
      siteManager = get(),
      snackbarManager = get(),
      kpnsHelper = get(),
      remoteFilePicker = get(),
      modifyMarkedPosts = get(),
      addOrRemoveBookmark = get(),
      loadChanCatalog = get(),
      localFilePicker = get(),
      appResources = get(),
      savedStateHandle = get()
    )
  }
  viewModel {
    Chan4CaptchaViewModel(
      proxiedOkHttpClient = get(),
      siteManager = get(),
      firewallBypassManager = get(),
      moshi = get(),
      loadChanCatalog = get(),
      chan4CaptchaSolverHelper = get()
    )
  }
  viewModel {
    DvachCaptchaScreenViewModel(
      proxiedOkHttpClient = get(),
      siteManager = get(),
      moshi = get(),
    )
  }

  viewModel {
    BookmarksScreenViewModel(
      androidHelpers = get(),
      bookmarksManager = get(),
      catalogPagesRepository = get(),
      reorderBookmarks = get(),
      deleteBookmarks = get(),
      toggleBookmarkWatchState = get(),
      restartBookmarkBackgroundWatcher = get()
    )
  }

  viewModel { AnimateableStackContainerViewModel() }
  viewModel { KurobaToolbarContainerViewModel() }
  viewModel { GlobalSearchScreenViewModel(catalogGlobalSearch = get()) }
  viewModel { PostCellIconViewModel(siteManager = get()) }

  viewModel {
    AppSettingsScreenViewModel(
      appContext = get(),
      appSettings = get(),
      androidHelpers = get(),
      appResources = get(),
      snackbarManager = get(),
      updateManager = get(),
      themeEngine = get(),
      restartBookmarkBackgroundWatcher = get()
    )
  }

  viewModel {
    RemoteImageSearchScreenViewModel(
      remoteImageSearchSettings = get(),
      yandexImageSearch = get()
    )
  }

  viewModel {
    Chan4LoginScreenViewModel(siteManager = get())
  }
  viewModel {
    DvachLoginScreenViewModel(siteManager = get())
  }
  viewModel {
    PostScreenshotScreenViewModel(androidHelpers = get())
  }
  viewModel {
    ThemesScreenViewModel(themeStorage = get(), themeEngine = get())
  }
  viewModel {
    DownloadsScreenViewModel(mediaSaver = get())
  }
  viewModel {
    KPNCScreenViewModel(
      sharedPrefs = get(),
      googleServicesChecker = get(),
      tokenUpdater = get(),
      accountRepository = get(),
    )
  }
}