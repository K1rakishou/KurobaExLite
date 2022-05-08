package com.github.k1rakishou.kurobaexlite.helpers.di

import android.app.Application
import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.github.k1rakishou.chan.core.mpv.MpvInitializer
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.boards.CatalogSelectionScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.captcha.Chan4CaptchaViewModel
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.navigation.HistoryScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupRepliesScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.CrossThreadFollowHistory
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.KurobaLruDiskCache
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.notification.ReplyNotificationsHelper
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.picker.LocalFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResourcesImpl
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.interactors.InstallMpvNativeLibrariesFromGithub
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.AddOrRemoveBookmark
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.ExtractRepliesToMyPosts
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.FetchThreadBookmarkInfo
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.UpdatePostSeenForBookmark
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.interactors.catalog.RetrieveSiteCatalogList
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.LoadMarkedPosts
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.ModifyMarkedPosts
import com.github.k1rakishou.kurobaexlite.interactors.navigation.LoadNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.navigation.ModifyNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.navigation.PersistNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.UpdateChanCatalogView
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.UpdateChanThreadView
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.CaptchaManager
import com.github.k1rakishou.kurobaexlite.managers.CatalogManager
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.ISiteManager
import com.github.k1rakishou.kurobaexlite.managers.LastVisitedEndpointManager
import com.github.k1rakishou.kurobaexlite.managers.MarkedPostManager
import com.github.k1rakishou.kurobaexlite.managers.NavigationHistoryManager
import com.github.k1rakishou.kurobaexlite.managers.PostBindProcessor
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.activity.MainActivityViewModel
import com.squareup.moshi.Moshi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

object DependencyGraph {

  fun initialize(
    application: KurobaExLiteApplication,
    appCoroutineScope: CoroutineScope
  ): List<Module> {
    val modules = mutableListOf<Module>()

    // Create everything eagerly to check for cycle dependencies when using dev builds
    val createAtStart = BuildConfig.FLAVOR_TYPE == 2

    modules += module(createdAtStart = createAtStart) {
      system(application, appCoroutineScope)
      generic()
      kurobaDiskLruCache()
      coilMediaDiskCache()
      coilImageLoader()
      interactors()
      managers()
      viewModels()
    }

    return modules
  }

  private fun Module.system(
    application: KurobaExLiteApplication,
    appCoroutineScope: CoroutineScope
  ) {
    single<Context> { application.applicationContext }
    single<Application> { application }
    single<KurobaExLiteApplication> { application }
    single<CoroutineScope> { appCoroutineScope }
  }

  private fun Module.generic() {
    single { KurobaExLiteDatabase.buildDatabase(application = get()) }
    single { ProxiedOkHttpClient() }
    single { GlobalConstants(get()) }
    single { Moshi.Builder().build() }
    single { PostCommentParser(siteManager = get()) }
    single { PostCommentApplier() }
    single { FullScreenHelpers(get()) }
    single { AndroidHelpers(application = get(), snackbarManager = get()) }
    single { ChanCache(androidHelpers = get()) }
    single { AppSettings(appContext = get(), moshi = get()) }
    single { Chan4DataSource(siteManager = get(), kurobaOkHttpClient = get(), moshi = get()) }
    single { PostBindProcessor(get()) }
    single { ThemeEngine() }
    single { MediaViewerPostListScroller() }
    single { CrossThreadFollowHistory() }
    single { ClickedThumbnailBoundsStorage() }
    single { ReplyNotificationsHelper() }
    single {
      ParsedPostDataCache(
        appContext = get(),
        coroutineScope = get(),
        postCommentParser = get(),
        postCommentApplier = get(),
        postReplyChainManager = get(),
        markedPostManager = get()
      )
    }
    single { MediaSaver(applicationContext = get(), androidHelpers = get(), proxiedOkHttpClient = get()) }
    single<AppResources> { AppResourcesImpl(appContext = get()) }
    single {
      LocalFilePicker(
        appContext = get(),
        appScope = get(),
        appSettings = get(),
        androidHelpers = get(),
        appResources = get(),
      )
    }
  }

  private fun Module.managers() {
    single { SiteManager(appContext = get()) }
    single<ISiteManager> { get<SiteManager>() }
    single { ApplicationVisibilityManager() }
    single { CatalogManager() }
    single { ChanThreadManager(siteManager = get(), chanCache = get()) }
    single { PostReplyChainManager() }
    single { ChanViewManager() }
    single { SnackbarManager(appContext = get()) }
    single { NavigationHistoryManager() }
    single { MarkedPostManager() }
    single { CaptchaManager() }
    single { LastVisitedEndpointManager(appScope = get()) }
    single { BookmarksManager() }

    single {
      GlobalUiInfoManager(
        appScope = get(),
        appResources = get(),
        appSettings = get()
      )
    }
  }

  private fun Module.viewModels() {
    viewModel {
      MainActivityViewModel()
    }
    viewModel {
      HomeScreenViewModel(
        siteManager = get(),
        captchaManager = get()
      )
    }
    viewModel {
      CatalogScreenViewModel(
        savedStateHandle = get()
      )
    }
    viewModel {
      ThreadScreenViewModel(
        savedStateHandle = get()
      )
    }
    viewModel { AlbumScreenViewModel() }
    viewModel { PopupRepliesScreenViewModel(savedStateHandle = get()) }
    viewModel { CatalogSelectionScreenViewModel() }
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
        mpvSettings = mpvSettings,
        mpvInitializer = mpvInitializer,
        proxiedOkHttpClient = get(),
        kurobaLruDiskCache = get(),
        installMpvNativeLibrariesFromGithub = get(),
        imageLoader = get(),
        mediaSaver = get(),
      )
    }
    viewModel { HistoryScreenViewModel() }
    viewModel {
      ReplyLayoutViewModel(
        captchaManager = get(),
        siteManager = get(),
        snackbarManager = get(),
        modifyMarkedPosts = get(),
        localFilePicker = get(),
        appResources = get(),
        savedStateHandle = get()
      )
    }
    viewModel {
      Chan4CaptchaViewModel(
        proxiedOkHttpClient = get(),
        siteManager = get(),
        moshi = get()
      )
    }
  }

  private fun Module.interactors() {
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
        proxiedOkHttpClient = get()
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
        kurobaExLiteDatabase = get(),
        replyNotificationsHelper = get(),
        loadChanThreadView = get(),
        extractRepliesToMyPosts = get(),
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
        bookmarksManager = get(),
        applicationVisibilityManager = get(),
        kurobaExLiteDatabase = get()
      )
    }
    single {
      LoadBookmarks(
        appContext = get(),
        appScope = get(),
        androidHelpers = get(),
        applicationVisibilityManager = get(),
        bookmarksManager = get(),
        kurobaExLiteDatabase = get()
      )
    }
    single {
      UpdatePostSeenForBookmark(
        appScope = get(),
        chanCache = get(),
        bookmarksManager = get(),
        kurobaExLiteDatabase = get(),
      )
    }

    single {
      LoadNavigationHistory(navigationHistoryManager = get(), kurobaExLiteDatabase = get())
    }
    single {
      ModifyNavigationHistory(navigationHistoryManager = get(), chanCache = get(), parsedPostDataCache = get())
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
  }

  @OptIn(ExperimentalCoilApi::class)
  private fun Module.coilMediaDiskCache() {
    single {
      val context: Context = get()
      val safeCacheDir = context.cacheDir.apply { mkdirs() }

      val diskCache = DiskCache.Builder()
        .directory(safeCacheDir.resolve("coil_media_cache"))
        .build()

      return@single diskCache
    }
  }

  private fun Module.kurobaDiskLruCache() {
    single {
      val applicationContext = get<Context>().applicationContext
      val androidHelpers = get<AndroidHelpers>()
      val diskCacheFile = File(applicationContext.cacheDir, "kuroba_disk_cache")

      val cacheSize = if (androidHelpers.isDevFlavor()) {
        32L * 1024 * 1024
      } else {
        256L * 1024 * 1024
      }

      return@single KurobaLruDiskCache(
        appContext = applicationContext,
        diskCacheDir = diskCacheFile,
        androidHelpers = get(),
        totalFileCacheDiskSizeBytes = cacheSize
      )
    }
  }

  @OptIn(ExperimentalCoilApi::class)
  private fun Module.coilImageLoader() {
    single<ImageLoader> {
      val applicationContext = get<Context>().applicationContext
      val diskCacheInit = { get<DiskCache>() }

      val imageLoader = ImageLoader.Builder(applicationContext)
        .apply {
          components {
            diskCache(diskCacheInit)
          }
        }.build()

      Coil.setImageLoader(imageLoader)
      return@single imageLoader
    }
  }

}