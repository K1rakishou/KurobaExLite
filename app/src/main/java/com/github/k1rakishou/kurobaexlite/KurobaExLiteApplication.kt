package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.util.Log
import com.github.k1rakishou.kurobaexlite.helpers.di.DependencyGraph
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.notifications.ReplyNotificationsHelper
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import kotlin.system.exitProcess
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.asLog
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent.inject

class KurobaExLiteApplication : Application() {
  private val applicationVisibilityManager: ApplicationVisibilityManager by inject(ApplicationVisibilityManager::class.java)
  private val replyNotificationsHelper: ReplyNotificationsHelper by inject(ReplyNotificationsHelper::class.java)

  private val appCoroutineScope = KurobaCoroutineScope()

  override fun onCreate() {
    super.onCreate()

    startKoin {
      modules(
        DependencyGraph.initialize(
          application = this@KurobaExLiteApplication,
          appCoroutineScope = appCoroutineScope
        )
      )
    }

    registerActivityLifecycleCallbacks(applicationVisibilityManager)
    replyNotificationsHelper.init()

    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
      // if there's any uncaught crash stuff, just dump them to the log and exit immediately
      logcatError { "Unhandled exception in thread: ${thread.name}, error: ${e.asLog()}" }
      exitProcess(-1)
    }

    LogcatLogger.install(KurobaExLiteLogger())
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