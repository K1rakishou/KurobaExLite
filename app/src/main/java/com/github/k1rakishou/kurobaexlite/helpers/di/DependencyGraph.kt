package com.github.k1rakishou.kurobaexlite.helpers.di

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.KurobaLruDiskCache
import com.github.k1rakishou.kurobaexlite.helpers.cache.site_data.SiteDataPersister
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.interactors.GetSiteBoardList
import com.github.k1rakishou.kurobaexlite.interactors.InstallMpvNativeLibrariesFromGithub
import com.github.k1rakishou.kurobaexlite.interactors.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.interactors.UpdateChanCatalogView
import com.github.k1rakishou.kurobaexlite.interactors.UpdateChanThreadView
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.ChanViewManager
import com.github.k1rakishou.kurobaexlite.managers.PostBindProcessor
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.boards.BoardSelectionScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.ui.screens.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply.PopupRepliesScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.CrossThreadFollowHistory
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
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

    modules += module {
      single<Context> { application.applicationContext }
      single<Application> { application }
      single<KurobaExLiteApplication> { application }
      single<CoroutineScope> { appCoroutineScope }
      single { KurobaExLiteDatabase.buildDatabase(application = get()) }
      single { ProxiedOkHttpClient() }
      single { GlobalConstants(get()) }
      single { Moshi.Builder().build() }
      single { PostCommentParser() }
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
      single {
        ParsedPostDataCache(
          appContext = get(),
          coroutineScope = get(),
          globalConstants = get(),
          postCommentParser = get(),
          postCommentApplier = get(),
          postReplyChainManager = get()
        )
      }
      single { SiteDataPersister(appContext = get(), moshi = get()) }

      kurobaDiskLruCache()
      coilMediaDiskCache()
      coilImageLoader()
      interactors()
      managers()
      viewModels()
    }

    return modules
  }

  private fun Module.managers() {
    single { SiteManager() }
    single { ChanThreadManager(siteManager = get(), chanCache = get()) }
    single { PostReplyChainManager() }
    single { ChanViewManager() }
    single { SnackbarManager(appContext = get()) }
    single { UiInfoManager(appContext = get(), appSettings = get(), coroutineScope = get()) }
  }

  private fun Module.viewModels() {
    viewModel {
      HomeScreenViewModel(
        application = get(),
        siteManager = get()
      )
    }
    viewModel {
      CatalogScreenViewModel(
        application = get(),
        savedStateHandle = get()
      )
    }
    viewModel {
      ThreadScreenViewModel(
        application = get<KurobaExLiteApplication>(),
        savedStateHandle = get()
      )
    }
    viewModel {
      PopupRepliesScreenViewModel(
        application = get(),
        savedStateHandle = get()
      )
    }
    viewModel {
      BoardSelectionScreenViewModel(application = get())
    }
    viewModel {
      MediaViewerScreenViewModel(
        application = get(),
        mpvSettings = MpvSettings(
          appContext = get(),
          androidHelpers = get()
        ),
        chanCache = get(),
        proxiedOkHttpClient = get(),
        kurobaLruDiskCache = get()
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
      GetSiteBoardList(
        siteManager = get(),
        siteDataPersister = get()
      )
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

      return@single ImageLoader.Builder(applicationContext)
        .apply {
          components {
            diskCache(diskCacheInit)
          }
        }.build()
    }
  }

}