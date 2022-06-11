package com.github.k1rakishou.kurobaexlite

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.github.k1rakishou.kurobaexlite.helpers.di.DependencyGraph
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.notifications.ReplyNotificationsHelper
import com.github.k1rakishou.kurobaexlite.managers.ApplicationVisibilityManager
import com.github.k1rakishou.kurobaexlite.ui.activity.CrashReportActivity
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.asLog
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent.inject

class KurobaExLiteApplication : Application() {
  private val applicationVisibilityManager: ApplicationVisibilityManager by inject(ApplicationVisibilityManager::class.java)
  private val replyNotificationsHelper: ReplyNotificationsHelper by inject(ReplyNotificationsHelper::class.java)

  private val globalExceptionHandler = CoroutineExceptionHandler { coroutineContext, exception ->
    logcatError {
      "[Global] Unhandled exception in coroutine: '${coroutineContext[CoroutineName.Key]?.name}', " +
        "error: ${exception.asLog()}"
    }

    showCrashReportActivity(exception)
    throw exception
  }

  private val appCoroutineScope = KurobaCoroutineScope(exceptionHandler = globalExceptionHandler)

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
      showCrashReportActivity(e)
    }

    LogcatLogger.install(KurobaExLiteLogger())
  }

  private fun showCrashReportActivity(e: Throwable?) {
    val bundle = Bundle()
      .apply { putSerializable(CrashReportActivity.EXCEPTION_KEY, rootCause(e)) }

    val intent = Intent(this, CrashReportActivity::class.java)
    intent.putExtra(CrashReportActivity.EXCEPTION_BUNDLE_KEY, bundle)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)

    exitProcess(-1)
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

  private fun rootCause(inputThrowable: Throwable?): Throwable? {
    var depth = 0
    var throwable = inputThrowable

    while (true) {
      val cause = throwable?.cause
        ?: break

      throwable = cause
      ++depth

      if (depth > 32) {
        logcatError(GLOBAL_TAG) { "Depth of throwable.cause exceeds the max allowed (32)" }
        break
      }
    }

    return throwable
  }

  companion object {
    const val GLOBAL_TAG = "KurobaExLite"
  }

}