package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.util.Log
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
      single {

      }
    }

    return modules
  }

  class KurobaExLiteLogger : LogcatLogger {

    override fun log(priority: LogPriority, tag: String, message: String) {
      when (priority) {
        LogPriority.VERBOSE -> Log.v(tag, message)
        LogPriority.DEBUG -> Log.d(tag, message)
        LogPriority.INFO -> Log.i(tag, message)
        LogPriority.WARN -> Log.w(tag, message)
        LogPriority.ERROR -> Log.e(tag, message)
        LogPriority.ASSERT -> Log.e(tag, message)
      }
    }

  }

}