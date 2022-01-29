package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.util.Log
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.ChanDataSource
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
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
      exitProcess(999)
    }

    startKoin {
      modules(provideModules())
    }

    LogcatLogger.install(KurobaExLiteLogger())
  }

  private fun provideModules(): List<Module> {
    val modules = mutableListOf<Module>()

    modules += module {
      single { ProxiedOkHttpClient() }
      single { GlobalConstants(this@KurobaExLiteApplication) }
      single { Moshi.Builder().build() }
      single { PostCommentParser() }
      single { PostCommentApplier() }
      single { SiteManager() }
      single { ChanThreadManager(get()) }
      single { UiInfoManager(this@KurobaExLiteApplication) }
      single { ChanDataSource(get(), get(), get()) }
      single { ThemeEngine() }

      viewModel { HomeScreenViewModel(get()) }
      viewModel { CatalogScreenViewModel(get(), get(), get(), get(), get()) }
      viewModel { ThreadScreenViewModel(get(), get(), get(), get(), get()) }
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