package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.chan.core.mpv.MpvInitializer
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.boards.CatalogSelectionScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.bookmarks.HistoryScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.captcha.chan4.Chan4CaptchaViewModel
import com.github.k1rakishou.kurobaexlite.features.captcha.dvach.DvachCaptchaScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
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
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.model.cache.ChanPostCache
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
      applicationVisibilityManager = get(),
    )
  }

  viewModel {
    AlbumScreenViewModel(
      appSettings = get(),
      appResources = get(),
      chanViewManager = get(),
      parsedPostDataCache = get(),
      chanPostCache = get<ChanPostCache>(),
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
      chanPostCache = get<ChanPostCache>(),
      siteManager = get(),
      proxiedOkHttpClient = get<ProxiedOkHttpClient>(),
      kurobaLruDiskCache = get(),
      installMpvNativeLibrariesFromGithub = get(),
      imageLoader = get(),
      mediaSaver = get(),
      postReplyChainManager = get(),
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
      proxiedOkHttpClient = get<ProxiedOkHttpClient>(),
      siteManager = get(),
      firewallBypassManager = get(),
      moshi = get(),
      loadChanCatalog = get(),
      chan4CaptchaSolverHelper = get()
    )
  }
  viewModel {
    DvachCaptchaScreenViewModel(
      proxiedOkHttpClient = get<ProxiedOkHttpClient>(),
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
}