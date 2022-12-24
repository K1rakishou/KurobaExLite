package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.interactors.InstallMpvNativeLibrariesFromGithub
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.AddOrRemoveBookmark
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.AddToHistoryAllCatalogThreads
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.BookmarkAllCatalogThreads
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.DeleteBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.ExtractRepliesToMyPosts
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.FetchThreadBookmarkInfo
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.PersistBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.ReorderBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.RestartBookmarkBackgroundWatcher
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.ToggleBookmarkWatchState
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.UpdateBookmarkInfoUponThreadOpen
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.UpdatePostSeenForBookmark
import com.github.k1rakishou.kurobaexlite.interactors.catalog.CatalogGlobalSearch
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadCatalogsForAllSites
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.interactors.catalog.RetrieveSiteCatalogList
import com.github.k1rakishou.kurobaexlite.interactors.image_search.YandexImageSearch
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.LoadMarkedPosts
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.ModifyMarkedPosts
import com.github.k1rakishou.kurobaexlite.interactors.navigation.LoadNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.navigation.ModifyNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.navigation.PersistNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.UpdateChanCatalogView
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.UpdateChanThreadView
import com.github.k1rakishou.kurobaexlite.model.cache.ChanPostCache
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import org.koin.core.module.Module

internal fun Module.interactors() {
  single {
    LoadChanThreadView(
      chanViewManager = get(),
      kurobaExLiteDatabase = get()
    )
  }
  single {
    UpdateChanThreadView(
      chanViewManager = get(),
      kurobaExLiteDatabase = get()
    )
  }
  single {
    UpdateChanCatalogView(
      chanViewManager = get()
    )
  }
  single {
    InstallMpvNativeLibrariesFromGithub(
      appContext = get(),
      moshi = get(),
      proxiedOkHttpClient = get<ProxiedOkHttpClient>()
    )
  }
  single {
    RetrieveSiteCatalogList(
      siteManager = get(),
      catalogManager = get(),
      kurobaExLiteDatabase = get()
    )
  }
  single {
    LoadChanCatalog(
      catalogManager = get(),
      kurobaExLiteDatabase = get()
    )
  }
  single {
    FetchThreadBookmarkInfo(
      siteManager = get(),
      bookmarksManager = get(),
      replyNotificationsHelper = get(),
      parsedPostDataCache = get(),
      loadChanThreadView = get(),
      extractRepliesToMyPosts = get(),
      persistBookmarks = get(),
      appSettings = get(),
      androidHelpers = get(),
    )
  }
  single {
    ExtractRepliesToMyPosts(
      appScope = get(),
      postCommentParser = get(),
      siteManager = get(),
      kurobaExLiteDatabase = get()
    )
  }
  single {
    AddOrRemoveBookmark(
      appContext = get(),
      androidHelpers = get(),
      appSettings = get(),
      bookmarksManager = get(),
      kurobaExLiteDatabase = get(),
      restartBookmarkBackgroundWatcher = get()
    )
  }
  single {
    LoadBookmarks(
      appScope = get(),
      bookmarksManager = get(),
      kurobaExLiteDatabase = get(),
      restartBookmarkBackgroundWatcher = get()
    )
  }
  single {
    UpdatePostSeenForBookmark(
      appScope = get(),
      chanPostCache = get<ChanPostCache>(),
      bookmarksManager = get(),
      kurobaExLiteDatabase = get(),
    )
  }
  single {
    ReorderBookmarks(kurobaExLiteDatabase = get())
  }
  single {
    DeleteBookmarks(
      appScope = get(),
      bookmarksManager = get(),
      kurobaExLiteDatabase = get()
    )
  }
  single {
    ToggleBookmarkWatchState(
      kurobaExLiteDatabase = get(),
      bookmarksManager = get(),
      restartBookmarkBackgroundWatcher = get()
    )
  }
  single {
    RestartBookmarkBackgroundWatcher(
      appContext = get(),
      appScope = get(),
      androidHelpers = get(),
      appSettings = get(),
      applicationVisibilityManager = get(),
    )
  }
  single {
    LoadNavigationHistory(navigationHistoryManager = get(), kurobaExLiteDatabase = get())
  }
  single {
    ModifyNavigationHistory(
      navigationHistoryManager = get(),
      chanPostCache = get<ChanPostCache>(),
      parsedPostDataCache = get(),
      appSettings = get()
    )
  }
  single {
    PersistNavigationHistory(navigationHistoryManager = get(), kurobaExLiteDatabase = get())
  }
  single {
    LoadMarkedPosts(markedPostManager = get(), kurobaExLiteDatabase = get())
  }
  single {
    ModifyMarkedPosts(markedPostManager = get(), kurobaExLiteDatabase = get())
  }
  single {
    CatalogGlobalSearch(
      globalSearchRepository = get(),
      parsedPostDataCache = get(),
      postCommentApplier = get(),
      themeEngine = get<ThemeEngine>()
    )
  }
  single { PersistBookmarks(bookmarksManager = get(), kurobaExLiteDatabase = get()) }
  single {
    UpdateBookmarkInfoUponThreadOpen(
      appScope = get(),
      bookmarksManager = get(),
      chanPostCache = get<ChanPostCache>(),
      parsedPostDataCache = get(),
      kurobaExLiteDatabase = get(),
    )
  }
  single {
    BookmarkAllCatalogThreads(
      appScope = get(),
      appSettings = get(),
      chanPostCache = get<ChanPostCache>(),
      bookmarksManager = get(),
      kurobaExLiteDatabase = get(),
      parsedPostDataCache = get(),
      restartBookmarkBackgroundWatcher = get()
    )
  }
  single {
    AddToHistoryAllCatalogThreads(
      appScope = get(),
      modifyNavigationHistory = get(),
      chanPostCache = get<ChanPostCache>()
    )
  }
  single {
    YandexImageSearch(
      proxiedOkHttpClient = get<ProxiedOkHttpClient>(),
      moshi = get()
    )
  }
  single {
    LoadCatalogsForAllSites(
      siteManager = get(),
      retrieveSiteCatalogList = get(),
    )
  }
}