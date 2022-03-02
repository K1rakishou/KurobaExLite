package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.util.Log
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.*
import com.github.k1rakishou.kurobaexlite.model.source.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.model.source.ChanThreadCache
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.boards.BoardSelectionScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.reply.PopupRepliesScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostBindProcessor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import com.squareup.moshi.Moshi
import logcat.LogPriority
import logcat.LogcatLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.system.exitProcess

class KurobaExLiteApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      // if there's any uncaught crash stuff, just dump them to the log and exit immediately
      Log.e("KurobaExLiteApplication", "Unhandled exception in thread: ${thread.name}", e)
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
      single { ProxiedOkHttpClient() }
      single { GlobalConstants(get()) }
      single { Moshi.Builder().build() }
      single { PostCommentParser() }
      single { PostCommentApplier() }

      single {
        ParsedPostDataCache(
          appContext = get(),
          globalConstants = get(),
          postCommentParser = get(),
          postCommentApplier = get(),
          postReplyChainManager = get()
        )
      }
      single { ChanThreadCache() }

      single { SiteManager() }
      single { ChanThreadManager(siteManager = get()) }
      single { PostReplyChainManager() }
      single { ChanThreadViewManager() }
      single { UiInfoManager(get()) }

      single { AppSettings(get()) }
      single { Chan4DataSource(siteManager = get(), kurobaOkHttpClient = get(), moshi = get()) }
      single { ThemeEngine() }
      single { PostBindProcessor(get()) }

      viewModel {
        HomeScreenViewModel(
          application = this@KurobaExLiteApplication,
          siteManager = get()
        )
      }
      viewModel {
        CatalogScreenViewModel(
          chanThreadManager = get(),
          chanThreadCache = get(),
          application = this@KurobaExLiteApplication,
          globalConstants = get(),
          themeEngine = get(),
          savedStateHandle = get()
        )
      }
      viewModel {
        ThreadScreenViewModel(
          chanThreadManager = get(),
          chanThreadCache = get(),
          application = this@KurobaExLiteApplication,
          globalConstants = get(),
          themeEngine = get(),
          savedStateHandle = get()
        )
      }
      viewModel {
        PopupRepliesScreenViewModel(
          chanThreadCache = get(),
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
    }

    return modules
  }

  class KurobaExLiteLogger : LogcatLogger {
    private val globalTag = "KurobaExLite |"

    override fun log(priority: LogPriority, tag: String, message: String) {
      when (priority) {
        LogPriority.VERBOSE -> Log.v("$globalTag $tag", message)
        LogPriority.DEBUG -> Log.d("$globalTag $tag", message)
        LogPriority.INFO -> Log.i("$globalTag $tag", message)
        LogPriority.WARN -> Log.w("$globalTag $tag", message)
        LogPriority.ERROR -> Log.e("$globalTag $tag", message)
        LogPriority.ASSERT -> Log.e("$globalTag $tag", message)
      }
    }

  }

}