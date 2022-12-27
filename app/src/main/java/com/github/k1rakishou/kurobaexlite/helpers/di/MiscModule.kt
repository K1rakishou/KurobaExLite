package com.github.k1rakishou.kurobaexlite.helpers.di

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.features.captcha.chan4.Chan4CaptchaSolverHelper
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.media.helpers.MediaViewerPostListScroller
import com.github.k1rakishou.kurobaexlite.features.posts.thread.CrossThreadFollowHistory
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppRestarter
import com.github.k1rakishou.kurobaexlite.helpers.Chan4BoardFlagsJsonAdapter
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru.KurobaLruDiskCache
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.notifications.ReplyNotificationsHelper
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.picker.LocalFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.picker.RemoteFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.PostBindProcessorCoordinator
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors.Chan4MathTagProcessor
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResourcesImpl
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.DialogSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.RemoteImageSearchSettings
import com.github.k1rakishou.kurobaexlite.managers.SiteProvider
import com.github.k1rakishou.kurobaexlite.model.cache.ChanPostCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.themes.ThemeStorage
import com.github.k1rakishou.kurobaexlite.ui.activity.MainActivityIntentHandler
import com.squareup.moshi.Moshi
import org.koin.core.module.Module
import java.io.File

internal fun Module.misc() {
  single { SiteProvider(appContext = get(), appScope = get()) }
  single { AppSettings(fileName = "app_settings", appContext = get(), moshi = get()) }
  single { RemoteImageSearchSettings(fileName = "remote_image_search_settings", appContext = get(), moshi = get()) }
  single { DialogSettings(fileName = "dialog_settings", appContext = get(), moshi = get()) }
  single { KurobaExLiteDatabase.buildDatabase(application = get()) }

  single { FileManager(appContext = get(), badPathSymbolResolutionStrategy = BadPathSymbolResolutionStrategy.ReplaceBadSymbols) }
  single { FileChooser(appContext = get()) }

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

  single {
    val context: Context = get()
    val safeCacheDir = context.cacheDir.apply { mkdirs() }

    val diskCache = DiskCache.Builder()
      .directory(safeCacheDir.resolve("coil_media_cache"))
      .build()

    return@single diskCache
  }

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

  single<ProxiedOkHttpClient> {
    ProxiedOkHttpClient(
      siteManager = get(),
      firewallBypassManager = get()
    )
  }

  single { GlobalConstants(get()) }
  single { createMoshi() }
  single { PostCommentParser(siteManager = get()) }
  single {
    PostCommentApplier(
      androidHelpers = get(),
      appSettings = get(),
      postBindProcessorCoordinator = get()
    )
  }
  single { FullScreenHelpers(get()) }
  single { AndroidHelpers(application = get(), snackbarManager = get()) }
  single<ThemeEngine> { ThemeEngine(appScope = get(), appSettings = get(), themeStorage = get()) }
  single<ThemeStorage> { ThemeStorage(appContext = get(), moshi = get(), fileManager = get()) }
  single { MediaViewerPostListScroller() }
  single { CrossThreadFollowHistory() }
  single { ClickedThumbnailBoundsStorage() }
  single { AppRestarter() }
  single {
    ParsedPostDataCache(
      appContext = get(),
      coroutineScope = get(),
      appSettings = get(),
      postCommentParser = get(),
      postCommentApplier = get(),
      postReplyChainManager = get(),
      markedPostManager = get()
    )
  }

  single {
    Chan4MathTagProcessor(
      chanPostCache = get<ChanPostCache>(),
      proxiedOkHttpClient = get<ProxiedOkHttpClient>(),
      appSettings = get()
    )
  }

  single {
    PostBindProcessorCoordinator(chan4MathTagProcessor = get(), appScope = get())
  }

  single {
    MediaSaver(
      appScope = get(),
      applicationContext = get(),
      androidHelpers = get(),
      globalConstants = get(),
      proxiedOkHttpClient = get<ProxiedOkHttpClient>(),
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
      proxiedOkHttpClient = get<ProxiedOkHttpClient>(),
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
      themeEngine = get<ThemeEngine>(),
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