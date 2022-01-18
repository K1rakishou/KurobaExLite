package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.util.Log
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import logcat.LogPriority
import logcat.LogcatLogger
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

class KurobaExLiteApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    startKoin {
      modules(provideModules())
    }

    LogcatLogger.install(KurobaExLiteLogger())
  }

  private fun KoinApplication.provideModules(): List<Module> {
    val modules = mutableListOf<Module>()

    modules += module {
      single { UiInfoManager() }
      single { ThemeEngine() }
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