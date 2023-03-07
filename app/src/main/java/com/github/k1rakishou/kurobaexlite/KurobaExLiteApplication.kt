package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.github.k1rakishou.kurobaexlite.helpers.di.DependencyGraph
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.notifications.ReplyNotificationsHelper
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.ui.activity.CrashReportActivity
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.asLog
import logcat.logcat
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent.inject
import kotlin.system.exitProcess

class KurobaExLiteApplication : Application() {
  private val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  private val applicationVisibilityManager: ApplicationVisibilityManager by inject(ApplicationVisibilityManager::class.java)
  private val replyNotificationsHelper: ReplyNotificationsHelper by inject(ReplyNotificationsHelper::class.java)

  private val globalExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    logcatError {
      "[Global] Unhandled exception in coroutine: '${coroutineContext[CoroutineName.Key]?.name}', " +
        "error: ${throwable.asLog()}"
    }

    showCrashReportActivity(throwable)
  }

  private val appCoroutineScope = KurobaCoroutineScope(exceptionHandler = globalExceptionHandler)

  override fun onCreate() {
    super.onCreate()

    LogcatLogger.install(KurobaExLiteLogger())
    logcat(TAG) { "=== Application started ===" }

    startKoin {
      modules(
        DependencyGraph.initialize(
          application = this@KurobaExLiteApplication,
          appCoroutineScope = appCoroutineScope
        )
      )
    }

    registerActivityLifecycleCallbacks(applicationVisibilityManager)
    themeEngine.init()
    replyNotificationsHelper.init()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      // if there's any uncaught crash stuff, just dump them to the log and exit immediately
      logcatError { "Unhandled exception in thread: ${thread.name}, error: ${throwable.asLog()}" }
      showCrashReportActivity(throwable)
    }
  }

  private fun showCrashReportActivity(throwable: Throwable): Nothing {
    val message = throwable.errorMessageOrClassName(userReadable = true)
    val stacktrace = throwable.stackTraceToString()

    val bundle = Bundle()
      .apply {
        putString(CrashReportActivity.EXCEPTION_CLASS_NAME_KEY, throwable::class.java.name)
        putString(CrashReportActivity.EXCEPTION_MESSAGE_KEY, message)
        putString(CrashReportActivity.EXCEPTION_STACKTRACE_KEY, stacktrace)
      }

    val intent = Intent(this, CrashReportActivity::class.java)
    intent.putExtra(CrashReportActivity.EXCEPTION_BUNDLE_KEY, bundle)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)

    exitProcess(-1)
  }

  private class KurobaExLiteLogger : LogcatLogger {
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

    private const val TAG = "KurobaExLiteApplication"
  }

}