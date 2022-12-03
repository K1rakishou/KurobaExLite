package com.github.k1rakishou.kurobaexlite.helpers.di

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import com.github.k1rakishou.chan.core.mpv.MpvInitializer
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.boards.CatalogSelectionScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.bookmarks.HistoryScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.captcha.Chan4CaptchaSolverHelper
import com.github.k1rakishou.kurobaexlite.features.captcha.Chan4CaptchaViewModel
import com.github.k1rakishou.kurobaexlite.features.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.login.Chan4LoginScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.DefaultPopupPostsScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.reply.MediaViewerPopupPostsScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.search.global.GlobalSearchScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.search.image.RemoteImageSearchScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.PostCellIconViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.PostFollowStack
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.features.settings.application.AppSettingsScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppRestarter
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.KurobaLruDiskCache
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.notifications.ReplyNotificationsHelper
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.picker.LocalFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.picker.RemoteFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResourcesImpl
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.DialogSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.RemoteImageSearchSettings
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
import com.github.k1rakishou.kurobaexlite.managers.PostBindProcessor
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.ReportManager
import com.github.k1rakishou.kurobaexlite.managers.RevealedSpoilerImages
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SiteProvider
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UpdateManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4BoardFlagsJsonAdapter
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.repository.CatalogPagesRepository
import com.github.k1rakishou.kurobaexlite.model.repository.GlobalSearchRepository
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.model.source.dvach.DvachDataSource
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.activity.MainActivityIntentHandler
import com.github.k1rakishou.kurobaexlite.ui.activity.MainActivityViewModel
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.AnimateableStackContainerViewModel
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

object DependencyGraph {

  fun initialize(
    application: KurobaExLiteApplication,
    appCoroutineScope: CoroutineScope
  ): List<Module> {
    val modules = mutableListOf<Module>()

    // Create everything eagerly to check for cycle dependencies when using dev builds
    val createAtStart = BuildConfig.FLAVOR_TYPE == AndroidHelpers.FlavorType.Development.rawType

    modules += module(createdAtStart = createAtStart) {
      system(application, appCoroutineScope)
      misc()
      model()
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
    single<NotificationManagerCompat> { NotificationManagerCompat.from(get()) }
    single<NotificationManager> { get<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    single<KurobaExLiteApplication> { application }
    single<CoroutineScope> { appCoroutineScope }
  }

  private fun Module.misc() {
    single { SiteProvider(appContext = get(), appScope = get()) }
    single { AppSettings(fileName = "app_settings", appContext = get(), moshi = get()) }
    single { RemoteImageSearchSettings(fileName = "remote_image_search_settings", appContext = get(), moshi = get()) }
    single { DialogSettings(fileName = "dialog_settings", appContext = get(), moshi = get()) }

    single { KurobaExLiteDatabase.buildDatabase(application = get()) }

    single {
      ProxiedOkHttpClient(
        siteManager = get(),
        firewallBypassManager = get()
      )
    }

    single { GlobalConstants(get()) }
    single { createMoshi() }
    single { PostCommentParser(siteManager = get()) }
    single { PostCommentApplier() }
    single { FullScreenHelpers(get()) }
    single { AndroidHelpers(application = get(), snackbarManager = get()) }
    single { PostBindProcessor(get()) }
    single { ThemeEngine() }
    single { MediaViewerPostListScroller() }
    single { PostFollowStack() }
    single { ClickedThumbnailBoundsStorage() }
    single { AppRestarter() }
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

    single {
      MediaSaver(
        appScope = get(),
        applicationContext = get(),
        androidHelpers = get(),
        globalConstants = get(),
        proxiedOkHttpClient = get(),
        parsedPostDataCache = get()
      )
    }

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

    single {
      RemoteFilePicker(
        appContext = get(),
        appScope = get(),
        proxiedOkHttpClient = get(),
      )
    }

    single {
      ReplyNotificationsHelper(
        appContext = get(),
        appScope = get(),
        appSettings = get(),
        androidHelpers = get(),
        imageLoader = get(),
        notificationManagerCompat = get(),
        notificationManager = get(),
        chanThreadManager = get(),
        bookmarksManager = get(),
        themeEngine = get(),
        postCommentParser = get(),
        persistBookmarks = get(),
      )
    }
    single {
      MainActivityIntentHandler(
        globalUiInfoManager = get(),
        bookmarksManager = get(),
        persistBookmarks = get(),
      )
    }

    single { Chan4CaptchaSolverHelper(moshi = get(), appContext = get()) }
  }

  private fun createMoshi(): Moshi {
    return Moshi.Builder()
      .add(Chan4BoardFlagsJsonAdapter())
      .build()
  }

  private fun Module.managers() {
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

  private fun Module.viewModels() {
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
        postFollowStack = get(),
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
        chanCache = get(),
        mediaSaver = get(),
      )
    }

    viewModel { DefaultPopupPostsScreenViewModel(savedStateHandle = get()) }
    viewModel { MediaViewerPopupPostsScreenViewModel(savedStateHandle = get()) }
    viewModel { CatalogSelectionScreenViewModel(retrieveSiteCatalogList = get()) }
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
        chanCache = get(),
        siteManager = get(),
        proxiedOkHttpClient = get(),
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
        proxiedOkHttpClient = get(),
        siteManager = get(),
        firewallBypassManager = get(),
        moshi = get(),
        loadChanCatalog = get(),
        chan4CaptchaSolverHelper = get()
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
        appContext = get(),
        appScope = get(),
        androidHelpers = get(),
        appSettings = get(),
        applicationVisibilityManager = get(),
        bookmarksManager = get(),
        kurobaExLiteDatabase = get(),
        restartBookmarkBackgroundWatcher = get()
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
        chanCache = get(),
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
        themeEngine = get()
      )
    }
    single { PersistBookmarks(bookmarksManager = get(), kurobaExLiteDatabase = get()) }
    single {
      UpdateBookmarkInfoUponThreadOpen(
        appScope = get(),
        bookmarksManager = get(),
        chanCache = get(),
        parsedPostDataCache = get(),
        kurobaExLiteDatabase = get(),
      )
    }

    single {
      BookmarkAllCatalogThreads(
        appContext = get(),
        appScope = get(),
        androidHelpers = get(),
        appSettings = get(),
        applicationVisibilityManager = get(),
        chanCache = get(),
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
        chanCache = get()
      )
    }

    single {
      YandexImageSearch(
        proxiedOkHttpClient = get(),
        moshi = get()
      )
    }
  }

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

  private fun Module.model() {
    single { Chan4DataSource(siteManager = get(), kurobaOkHttpClient = get(), moshi = get()) }
    single { DvachDataSource(siteManager = get(), kurobaOkHttpClient = get(), moshi = get()) }

    single { ChanCache(androidHelpers = get()) }

    single {
      CatalogPagesRepository(
        appScope = get(),
        siteManager = get(),
      )
    }
    single { GlobalSearchRepository(siteManager = get()) }
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

  private fun Module.coilImageLoader() {
    single<ImageLoader> {
      val applicationContext = get<Context>().applicationContext
      val diskCacheInit = { get<DiskCache>() }

      val imageLoader = ImageLoader.Builder(applicationContext)
        .apply {
          components {
            diskCache(diskCacheInit)
            add(VideoFrameDecoder.Factory())
          }
        }.build()

      Coil.setImageLoader(imageLoader)
      return@single imageLoader
    }
  }

}