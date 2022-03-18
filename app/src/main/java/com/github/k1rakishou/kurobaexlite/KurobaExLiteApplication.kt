package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.interactors.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.interactors.UpdateChanThreadView
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadViewManager
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
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply.PopupRepliesScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenToolbarActionHandler
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import com.squareup.moshi.Moshi
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.asLog
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

class KurobaExLiteApplication : Application() {
  private val appCoroutineScope = KurobaCoroutineScope()

  // TODO(KurobaEx): [Click to expand] doesn't work
  // TODO(KurobaEx): PullToRefresh in catalog scrolls to the middle of the catalog
  // TODO(KurobaEx): Text selection doesn't work.
  // TODO(KurobaEx): When using search on BoardSelectionScreen and then navigating to a board without
  //  closing the search first will keep the search toolbar but loose the search query.

  override fun onCreate() {
    super.onCreate()

    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      // if there's any uncaught crash stuff, just dump them to the log and exit immediately
      logcatError { "Unhandled exception in thread: ${thread.name}, error: ${e.asLog()}" }
      exitProcess(-1)
    }

    startKoin {
      modules(provideModules())
    }

    LogcatLogger.install(KurobaExLiteLogger())
  }

  private fun provideModules(): List<Module> {
    val modules = mutableListOf<Module>()

    modules += module {
      single { this@KurobaExLiteApplication.applicationContext }
      single<Application> { this@KurobaExLiteApplication }
      single<CoroutineScope> { appCoroutineScope }
      single { KurobaExLiteDatabase.buildDatabase(this@KurobaExLiteApplication) }
      single { ProxiedOkHttpClient() }
      single { GlobalConstants(get()) }
      single { Moshi.Builder().build() }
      single { PostCommentParser() }
      single { PostCommentApplier() }
      single { FullScreenHelpers(get()) }
      single { AndroidHelpers(get()) }

      mediaDiskCache()
      coilImageLoader()

      single {
        LoadChanThreadView(
          chanThreadViewManager = get(),
          kurobaExLiteDatabase = get()
        )
      }
      single {
        UpdateChanThreadView(
          chanThreadViewManager = get(),
          kurobaExLiteDatabase = get()
        )
      }

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
      single { ChanCache() }
      single { SiteManager() }
      single { ChanThreadManager(siteManager = get()) }
      single { PostReplyChainManager() }
      single { ChanThreadViewManager() }
      single { SnackbarManager(appContext = get()) }
      single { UiInfoManager(appContext = get(), appSettings = get(), coroutineScope = get()) }
      single { AppSettings(appContext = get(), moshi = get()) }
      single { Chan4DataSource(siteManager = get(), kurobaOkHttpClient = get(), moshi = get()) }
      single { ThemeEngine() }
      single { PostBindProcessor(get()) }
      single {
        ThreadScreenToolbarActionHandler(
          siteManager = get(),
          snackbarManager = get(),
          androidHelpers = get()
        )
      }

      viewModel {
        HomeScreenViewModel(
          application = this@KurobaExLiteApplication,
          siteManager = get()
        )
      }
      viewModel {
        CatalogScreenViewModel(
          chanThreadManager = get(),
          chanCache = get(),
          application = this@KurobaExLiteApplication,
          globalConstants = get(),
          themeEngine = get(),
          savedStateHandle = get()
        )
      }
      viewModel {
        ThreadScreenViewModel(
          chanThreadManager = get(),
          chanCache = get(),
          application = this@KurobaExLiteApplication,
          globalConstants = get(),
          themeEngine = get(),
          savedStateHandle = get()
        )
      }
      viewModel {
        PopupRepliesScreenViewModel(
          chanCache = get(),
          postReplyChainManager = get(),
          application = this@KurobaExLiteApplication,
          globalConstants = get(),
          themeEngine = get(),
          savedStateHandle = get()
        )
      }
      viewModel {
        BoardSelectionScreenViewModel(
          application = this@KurobaExLiteApplication,
          siteManager = get()
        )
      }
      viewModel {
        MediaViewerScreenViewModel(
          chanCache = get(),
          proxiedOkHttpClient = get(),
          diskCache = get()
        )
      }
    }

    return modules
  }

  @OptIn(ExperimentalCoilApi::class)
  private fun Module.mediaDiskCache() {
    single {
      val context: Context = get()
      val safeCacheDir = context.cacheDir.apply { mkdirs() }

      return@single DiskCache.Builder()
        .directory(safeCacheDir.resolve("media_cache"))
        .build()
    }
  }

  @OptIn(ExperimentalCoilApi::class)
  private fun Module.coilImageLoader() {
    single {
      val context: Context = get()
      val diskCacheInit = { get<DiskCache>() }

      return@single ImageLoader.Builder(context).apply {
        components {
          diskCache(diskCacheInit)
        }
      }
    }
  }

  class KurobaExLiteLogger : LogcatLogger {
    override fun log(priority: LogPriority, tag: String, message: String) {
      when (priority) {
        LogPriority.VERBOSE -> Log.v("$GLOBAL_TAG | $tag", message)
        LogPriority.DEBUG -> Log.d("$GLOBAL_TAG | $tag", message)
        LogPriority.INFO -> Log.i("$GLOBAL_TAG | $tag", message)
        LogPriority.WARN -> Log.w("$GLOBAL_TAG | $tag", message)
        LogPriority.ERROR -> Log.e("$GLOBAL_TAG | $tag", message)
        LogPriority.ASSERT -> Log.e("$GLOBAL_TAG | $tag", message)
      }
    }
  }

  companion object {
    const val GLOBAL_TAG = "KurobaExLite"
  }

}