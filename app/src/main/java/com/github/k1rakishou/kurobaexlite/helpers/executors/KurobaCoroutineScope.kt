package com.github.k1rakishou.kurobaexlite.helpers.executors

import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import logcat.asLog

class KurobaCoroutineScope(
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
  private val exceptionHandler: CoroutineExceptionHandler? = null
) : CoroutineScope {
  private val job = SupervisorJob()

  @Suppress("IfThenToElvis")
  private val handler by lazy {
    if (exceptionHandler != null) {
      exceptionHandler
    } else {
      CoroutineExceptionHandler { coroutineContext, exception ->
        logcatError {
          "Unhandled exception in coroutine: '${coroutineContext[CoroutineName.Key]?.name}', " +
            "error: ${exception.asLog()}"
        }

        throw exception
      }
    }
  }

  override val coroutineContext: CoroutineContext
    get() = job + dispatcher + handler

  fun cancelChildren() {
    job.cancelChildren()
  }

  fun cancel() {
    job.cancel()
  }

}